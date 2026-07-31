package io.github.identityguard.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * 单个仓库最近一次校验结果的快照。
 *
 * 由监听器在后台预计算并缓存，供 Commit 前校验**只读复用**——
 * `beforeCheckin` 在 EDT 上执行，绝不能在那里跑外部 git（§3.4 / F3.1.5）。
 *
 * @param protected           是否命中保护范围（F2.2）
 * @param matchedAccountLogin 命中的账号登录名（小写）；未命中为 null
 * @param effectiveEmail      最终生效邮箱；null = 未设置或本轮未完成读取
 * @param effectiveName       最终生效 user.name，供 Commit 前弹窗展示（v2.3.1）；null = 未设置/读取失败
 * @param mismatch            是否需要提示（已综合忽略/宽松放行前的原始不匹配判定见校验流程）
 */
data class CheckSnapshot(
    val repoRootPath: String,
    val repoPresentableUrl: String,
    val remoteFingerprint: String,
    val protected: Boolean,
    val matchedAccountLogin: String?,
    val privacyEmail: String?,
    val verifiedEmails: List<String>,
    val effectiveEmail: String?,
    val effectiveName: String?,
    val mismatch: Boolean
)

/**
 * Remote 快照节流 + 校验结果缓存（project 级、仅内存，设计文档 F2.4）。
 *
 * `GitRepositoryChangeListener` 在任何仓库更新（fetch/status/分支切换）时都会触发，
 * 频率很高。这里维护每个仓库的 Remote URL 集合快照，
 * 仅当集合变化（或强制模式）时调用方才需要重新执行完整校验，
 * 避免高频事件反复 fork git 进程。
 */
@Service(Service.Level.PROJECT)
class RemoteSnapshotService {

    /** 仓库根路径 -> 上次见到的全部 Remote URL 集合 */
    private val lastUrls = HashMap<String, Set<String>>()
    private val snapshots = HashMap<String, CheckSnapshot>()

    /**
     * 更新 Remote 快照。
     * @return true = URL 集合相对上次有变化（或首次见到该仓库），调用方应执行完整校验
     */
    @Synchronized
    fun updateIfChanged(repoRootPath: String, currentUrls: Set<String>): Boolean {
        val previous = lastUrls.put(repoRootPath, currentUrls)
        return previous != currentUrls
    }

    @Synchronized
    fun putSnapshot(snapshot: CheckSnapshot) {
        snapshots[snapshot.repoRootPath] = snapshot
    }

    @Synchronized
    fun getSnapshot(repoRootPath: String): CheckSnapshot? = snapshots[repoRootPath]

    /** Commit 前校验：所有处于不匹配状态的快照 */
    @Synchronized
    fun mismatchedSnapshots(): List<CheckSnapshot> = snapshots.values.filter { it.mismatch }

    /** 账号集合变化时清空，迫使全量重新校验 */
    @Synchronized
    fun clear() {
        lastUrls.clear()
        snapshots.clear()
    }

    companion object {
        fun getInstance(project: Project): RemoteSnapshotService = project.service()
    }
}
