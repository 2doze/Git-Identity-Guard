package io.github.identityguard.util

/**
 * Git 身份比较与隐私邮箱合成工具（设计文档 F1.1/F2.3）。
 */
object IdentityUtil {

    private const val NOREPLY_SUFFIX = "@users.noreply.github.com"

    /**
     * 判断两个邮箱是否相同：先 trim 再忽略大小写。
     *
     * null 表示 user.email 未设置——按 F2.3 约定"未设置视为不匹配"，
     * 因此任一参数为 null 时恒返回 false。
     */
    fun emailsMatch(effective: String?, expected: String?): Boolean = sameTrimmed(effective, expected)

    /**
     * 判断两个用户名是否相同：与邮箱同一套规则（trim + 忽略大小写，null 视为不匹配）。
     *
     * 隐私身份 = (noreply 邮箱 + GitHub 用户名) 这一对；用户名偏差同样是隐私泄漏，
     * 故纳入不匹配判定（v2.3.1 最终修订）。GitHub 用户名本身大小写不敏感，比较亦忽略大小写。
     */
    fun namesMatch(effective: String?, expected: String?): Boolean = sameTrimmed(effective, expected)

    /** trim + 忽略大小写的相等比较；任一为 null 视为不匹配 */
    private fun sameTrimmed(effective: String?, expected: String?): Boolean {
        if (effective == null || expected == null) return false
        return effective.trim().equals(expected.trim(), ignoreCase = true)
    }

    /** 邮箱规范化形式（trim + 小写），用于去重键等需要稳定键的场景；null 归一为空串 */
    fun canonicalEmail(email: String?): String = email?.trim()?.lowercase() ?: ""

    /** 是否为 GitHub noreply 隐私邮箱（兼容新式 `id+login@` 与旧式 `login@` 两种形式） */
    fun isNoreplyEmail(email: String?): Boolean =
        email != null && email.trim().endsWith(NOREPLY_SUFFIX, ignoreCase = true)

    /**
     * 合成新式隐私邮箱：`<数字ID>+<login>@users.noreply.github.com`（F1.1）。
     * 新式格式在所有隐私设置下均有效，是 API 获取失败时的回退目标形式。
     */
    fun synthesizeNoreplyEmail(numericId: Long, login: String): String =
        "$numericId+${login.trim().lowercase()}$NOREPLY_SUFFIX"
}
