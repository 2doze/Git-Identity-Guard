package io.github.identityguard.github

import io.github.identityguard.util.IdentityUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException
import org.jetbrains.plugins.github.util.GHCompatibilityUtil
import java.io.IOException

/** 隐私邮箱获取结果 */
sealed class PrivacyEmailFetchResult {

    /**
     * @param privacyEmail   用于校验的隐私邮箱
     * @param verifiedEmails 该账号已验证邮箱列表（宽松模式放行条件，F3.2）
     * @param usedFallback   是否走了 "GET /user 合成" 回退路径（设置页状态展示用）
     */
    data class Success(
        val privacyEmail: String,
        val verifiedEmails: List<String>,
        val usedFallback: Boolean
    ) : PrivacyEmailFetchResult()

    /**
     * @param reasonKey 失败原因对应的消息包 key（由 UI 层本地化展示）
     * @param detail    原始异常信息，便于排查
     */
    data class Failure(val reasonKey: String, val detail: String?) : PrivacyEmailFetchResult()
}

/**
 * 隐私邮箱获取器（设计文档 F1.2）。
 *
 * 策略：
 * 1. 优先 `GET /user/emails` 直接取 noreply 条目（用户开启隐私保护时最准确）；
 * 2. 拿不到 noreply 条目时（未开启隐私保护），`GET /user` 取数字 ID 合成
 *    `<id>+<login>@users.noreply.github.com`——新式格式在所有隐私设置下均有效；
 * 3. `/user/emails` 返回 403（令牌缺 user:email scope）时按 F1.2.3 直接降级到合成路径，
 *    而不是整体失败。
 *
 * 注意：本类会发起网络请求，**必须在后台线程调用**（§3.4）。
 */
object PrivacyEmailFetcher {

    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(PrivacyEmailFetcher::class.java)

    fun fetch(account: GithubAccount, project: Project): PrivacyEmailFetchResult {
        // F5 非目标：仅保护 github.com，GHE 账号直接拒绝
        if (!account.server.isGithubDotCom) {
            return PrivacyEmailFetchResult.Failure("error.ghe", account.server.host)
        }
        return try {
            // 2025.3+ 的凭据获取方式:先取令牌,再用 (server, token) 创建执行器。
            // (2023.1 的 GithubApiRequestExecutorManager.getExecutor(account, project) 已被移除;
            //  GHCompatibilityUtil 是官方提供的兼容入口。)
            // getOrRequestToken 在令牌缺失时可能弹出重新登录对话框;拿不到令牌按认证失败处理。
            val token = GHCompatibilityUtil.getOrRequestToken(account, project)
                ?: return PrivacyEmailFetchResult.Failure("error.auth", "token unavailable")
            val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)
            val verified = mutableListOf<String>()

            // ---- 第 1 步：/user/emails ----
            var noreply: String? = null
            try {
                val emails = executor.execute(GetUserEmailsRequest(account.server))
                for (entry in emails) {
                    if (entry.verified && entry.email.isNotBlank()) verified.add(entry.email)
                    if (noreply == null && IdentityUtil.isNoreplyEmail(entry.email)) {
                        noreply = entry.email.trim()
                    }
                }
            } catch (e: GithubStatusCodeException) {
                // 403 = 令牌缺 user:email scope（F1.2.3）；
                // 404 = fine-grained PAT 等令牌对该端点无权限（GitHub 以 404 隐藏资源存在性）。
                // 两种都降级到 /user 合成路径；其他状态码按 API 错误继续上抛统一处理
                if (e.statusCode != 403 && e.statusCode != 404) throw e
                LOG.info("[GitIdentityGuard] /user/emails 返回 ${e.statusCode}，降级到合成路径")
            }
            if (noreply != null) {
                return PrivacyEmailFetchResult.Success(noreply, verified, usedFallback = false)
            }

            // ---- 第 2 步：/user 合成回退 ----
            val user = executor.execute(GetUserRequest(account.server))
            if (user.id <= 0) {
                return PrivacyEmailFetchResult.Failure("error.no.id", null)
            }
            PrivacyEmailFetchResult.Success(
                IdentityUtil.synthesizeNoreplyEmail(user.id, account.name),
                verified,
                usedFallback = true
            )
        } catch (e: ProcessCanceledException) {
            // 取消必须继续上抛，不能被当作普通失败吞掉
            throw e
        } catch (e: GithubRateLimitExceededException) {
            PrivacyEmailFetchResult.Failure("error.rate.limit", e.message)
        } catch (e: GithubMissingTokenException) {
            PrivacyEmailFetchResult.Failure("error.auth", e.message)
        } catch (e: GithubAuthenticationException) {
            PrivacyEmailFetchResult.Failure("error.auth", e.message)
        } catch (e: GithubStatusCodeException) {
            PrivacyEmailFetchResult.Failure("error.auth", "HTTP ${e.statusCode}")
        } catch (e: IOException) {
            PrivacyEmailFetchResult.Failure("error.network", e.message)
        } catch (e: Exception) {
            PrivacyEmailFetchResult.Failure("error.unknown", e.toString())
        }
    }

    /**
     * GET /user/emails —— SDK 无内置请求,基于公开的 Json 基类自定义。
     * 2025.3 移除了 Get.JsonList,改用 Get.Json + 数组类型反序列化 JSON 数组。
     */
    private class GetUserEmailsRequest(server: GithubServerPath) :
        GithubApiRequest.Get.Json<Array<GHUserEmailDto>>(
            "${server.toApiUrl()}/user/emails",
            Array<GHUserEmailDto>::class.java,
            "load github user emails"
        )

    /** GET /user —— SDK 内置 DTO 缺数字 id 字段，故自定义 */
    private class GetUserRequest(server: GithubServerPath) :
        GithubApiRequest.Get.Json<GHUserDto>(
            "${server.toApiUrl()}/user",
            GHUserDto::class.java,
            "load github user"
        )
}
