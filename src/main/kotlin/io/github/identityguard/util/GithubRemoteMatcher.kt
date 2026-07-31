package io.github.identityguard.util

/** 与平台无关的 Remote 描述（与 Git4Idea 类型解耦，便于纯单元测试） */
data class RemoteRef(val name: String, val urls: List<String>)

/** 命中结果：命中的仓库标识、具体命中的 URL 及其所属 Remote 名 */
data class RepoAccountMatch(val repoId: GithubRepoId, val matchedUrl: String, val remoteName: String)

/**
 * Remote 匹配器（设计文档 F2.2/F2.3）。
 *
 * 保护边界：Remote URL 标准化后的 owner 等于某个已登录 GitHub 账号用户名（大小写不敏感）。
 * 多 Remote/多账号命中时优先级：`origin` 优先，其余按传入顺序确定性选择（F2.3 步骤 3）。
 */
object GithubRemoteMatcher {

    /**
     * @param remotes      仓库的 Remote 列表（调用方应合并 urls 与 pushUrls）
     * @param accountLogins 已登录账号的用户名集合
     * @return 命中账号 login（小写）到命中结果的映射；**迭代顺序即优先级顺序**（origin 命中的排最前）。
     *         空 Map = 未命中保护范围。
     */
    fun findMatchedAccounts(
        remotes: List<RemoteRef>,
        accountLogins: Collection<String>
    ): Map<String, RepoAccountMatch> {
        if (remotes.isEmpty() || accountLogins.isEmpty()) return emptyMap()

        val logins = accountLogins
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (logins.isEmpty()) return emptyMap()

        // 稳定排序：origin 提到最前，其余 Remote 保持原有顺序
        val ordered = remotes.sortedBy { if (it.name.equals("origin", ignoreCase = true)) 0 else 1 }

        // LinkedHashMap 保证结果迭代顺序 = 优先级顺序
        val result = LinkedHashMap<String, RepoAccountMatch>()
        for (remote in ordered) {
            for (url in remote.urls) {
                val id = GithubUrlNormalizer.normalize(url) ?: continue
                // 同一账号在更高优先级 Remote 上已命中则不覆盖
                if (id.owner in logins && id.owner !in result) {
                    result[id.owner] = RepoAccountMatch(id, url, remote.name)
                }
            }
        }
        return result
    }
}
