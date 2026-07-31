package io.github.identityguard.util

/**
 * 通知去重键——四元组（设计文档 F4.1）：
 * 仓库标识 + 命中账号 + 当前生效邮箱 + 目标隐私邮箱。
 *
 * 当前邮箱参与键意味着：用户手动把邮箱改成另一个错误值时会产生新组合、可以再次提示；
 * 状态未变化时同一组合只提示一次。
 *
 * @param currentEmail 规范化后的当前邮箱（未设置为空串，见 [IdentityUtil.canonicalEmail]）
 * @param targetEmail  规范化后的隐私邮箱
 */
data class PromptKey(
    val repoRootPath: String,
    val remoteFingerprint: String,
    val accountLogin: String,
    val currentEmail: String,
    val targetEmail: String
)

/**
 * 忽略键——二元组（设计文档 F4.1）：仓库标识 + 命中账号。
 *
 * 与去重键的关键区别：忽略与具体邮箱值**无关**——
 * 用户选择"忽略该仓库"后，无论邮箱如何变化，该仓库与该账号的匹配都不再提示。
 */
data class IgnoreKey(
    val repoRootPath: String,
    val remoteFingerprint: String,
    val accountLogin: String
) {
    companion object {
        fun from(promptKey: PromptKey): IgnoreKey =
            IgnoreKey(promptKey.repoRootPath, promptKey.remoteFingerprint, promptKey.accountLogin)
    }
}

/**
 * 仓库标识 / Remote 指纹生成（设计文档 F4.1 与 §5 注意事项 3）。
 *
 * 忽略列表不能只按"仓库路径"存储：仓库被重建或 Remote 整体更换后，
 * 旧路径上的忽略记录不应被新仓库错误继承。指纹由全部 Remote URL 的标准化结果导出，
 * 与仓库路径组合后能识别这类变化。
 */
object RepoKeys {

    /**
     * 由 Remote URL 集合生成指纹：
     * 取所有 URL 标准化后的 owner/repo，去重排序后拼接，输出"数量-哈希"。
     * 无任何可识别 GitHub Remote 时返回固定值（此时仓库本身不命中保护范围，指纹仅用于键完整性）。
     */
    fun remoteFingerprint(urls: Collection<String>): String {
        val ids = urls
            .mapNotNull { GithubUrlNormalizer.normalize(it) }
            .map { it.toString() }
            .distinct()
            .sorted()
        if (ids.isEmpty()) return "no-github-remote"
        return "${ids.size}-${Integer.toHexString(ids.joinToString("|").hashCode())}"
    }
}
