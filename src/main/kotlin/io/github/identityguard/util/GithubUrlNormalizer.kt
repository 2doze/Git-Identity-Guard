package io.github.identityguard.util

/**
 * 标准化后的 GitHub 仓库标识。
 *
 * owner/repo 统一以小写存储——GitHub 用户名与仓库名在 URL 解析层面大小写不敏感
 * （github.com/UserName 与 github.com/username 指向同一账号），
 * 小写规范化可以避免 Remote 中大小写与登录名不一致时漏判（设计文档 F2.1/F2.3）。
 */
data class GithubRepoId(val owner: String, val repo: String) {
    override fun toString(): String = "$owner/$repo"
}

/**
 * GitHub Remote URL 标准化器（设计文档 F2.1）。
 *
 * 支持的形式：
 * - `https://github.com/<owner>/<repo>`（可带 `.git` 后缀、尾部斜杠、`:port`、`www.` 前缀）
 * - `http://github.com/...`（同上）
 * - `git@github.com:<owner>/<repo>`（scp-like，`.git` 后缀可选——该无后缀形式很常见，v2.2 曾遗漏）
 * - `ssh://git@github.com/<owner>/<repo>.git`（可带端口 `ssh://git@github.com:22/...`）
 *
 * 明确不支持（显式非目标，见 F5）：
 * - `git://` 协议；
 * - `~/.ssh/config` 主机别名（如 `git@gh:owner/repo`）——无法获知别名到 github.com 的映射；
 * - GitHub Enterprise 域名（仅保护 github.com）。
 */
object GithubUrlNormalizer {

    private const val GITHUB_HOST = "github.com"

    /** owner/repo 段的合法字符集（GitHub 实际允许字母数字与 . _ - ） */
    private val SEGMENT = Regex("[A-Za-z0-9._-]+")

    /** scp-like 形式：`[user@]host:path`，要求整体不含协议前缀 */
    private val SCP_LIKE = Regex("^([^@/]+@)?([^:/]+):(.+)$")

    /**
     * 将任意 Remote URL 规范化为 [GithubRepoId]；无法识别或不属于 github.com 时返回 null。
     */
    fun normalize(rawUrl: String): GithubRepoId? {
        val url = rawUrl.trim()
        if (url.isEmpty()) return null
        val path = extractPath(url) ?: return null
        return toRepoId(path)
    }

    /**
     * 按 URL 形式分流，提取 host 之后的仓库路径部分（可能仍带 .git 后缀、多余斜杠）。
     * 返回 null 表示 host 不是 github.com 或形式不受支持。
     */
    private fun extractPath(url: String): String? = when {
        // 1. 显式 http/https 协议
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true) -> {
            val rest = url.substringAfter("://")
            // authority 段可能带 userinfo(user@host:port);
            // substringAfter("@") 在无 @ 时返回原串,恰好兼容两种情况
            val host = rest.substringBefore("/").substringAfter("@").substringBefore(":")
            if (!isGithubHost(host)) null else rest.substringAfter("/", "")
        }
        // 2. 显式 ssh 协议：ssh://[user@]github.com[:port]/owner/repo
        url.startsWith("ssh://", ignoreCase = true) -> {
            val rest = url.substring("ssh://".length)
            val withoutUser = if ("@" in rest.substringBefore("/")) rest.substringAfter("@") else rest
            val host = withoutUser.substringBefore("/").substringBefore(":")
            if (!isGithubHost(host)) null else withoutUser.substringAfter("/", "")
        }
        // 3. 其他带协议的（git:// 等）显式不支持
        "://" in url -> null
        // 4. scp-like：[user@]github.com:owner/repo(.git)?
        else -> {
            val m = SCP_LIKE.matchEntire(url) ?: return null
            if (!isGithubHost(m.groupValues[2])) null else m.groupValues[3]
        }
    }

    /** 主机名判定：忽略大小写，容忍 www. 前缀 */
    private fun isGithubHost(host: String): Boolean =
        host.trim().lowercase().removePrefix("www.") == GITHUB_HOST

    /**
     * 清洗路径并要求恰好为 owner/repo 两段：
     * gist（host 不同，已排除）、`/owner` 单段、`/owner/repo/wiki` 多段均不命中（F2.1 规则 3）。
     */
    private fun toRepoId(rawPath: String): GithubRepoId? {
        var clean = rawPath.trim().trim('/')
        // `.git` 后缀大小写不敏感地剥离（Remote 里偶见 `.GIT`）
        if (clean.endsWith(".git", ignoreCase = true)) {
            clean = clean.dropLast(4).trim('/')
        }
        val segments = clean.split("/")
        if (segments.size != 2) return null
        val owner = segments[0]
        val repo = segments[1]
        if (!SEGMENT.matches(owner) || !SEGMENT.matches(repo)) return null
        return GithubRepoId(owner.lowercase(), repo.lowercase())
    }
}
