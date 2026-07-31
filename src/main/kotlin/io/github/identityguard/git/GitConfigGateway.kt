package io.github.identityguard.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/** 配置键读取结果 */
sealed class ConfigReadResult {
    /** value == null 表示该键未设置（system/global/local 均无） */
    data class Value(val value: String?) : ConfigReadResult()

    /** git 执行本身失败（非仓库、git 二进制缺失等），本轮校验应跳过而非报错给用户（§6.3 错误分级） */
    data class ExecError(val message: String) : ConfigReadResult()
}

/** 本地配置写入结果 */
sealed class ConfigWriteResult {
    object Success : ConfigWriteResult()
    data class Failure(val reason: String) : ConfigWriteResult()
}

/**
 * Git 配置读写网关（设计文档 §3.3）。
 *
 * 一律通过 git 命令行（Git4Idea）而不是手工解析/拼接 `.git/config` 文件：
 * - 读取走 `git config <key>`，天然覆盖全局配置中的 `includeIf` 条件包含（F2.3）；
 * - 写入走 `git config --local`，submodule / linked worktree 下 `.git` 是文件而非目录，
 *   手工拼路径会写错位置或失败。
 *
 * 多个键（user.email / user.name）必须拆分为多次独立写入（§3.3.4），
 * 不应假设一次 git config 命令可以同时设置多个键值。
 */
object GitConfigGateway {

    private val LOG = Logger.getInstance(GitConfigGateway::class.java)

    /** 读取仓库**最终生效**的 user.email（合并 system/global/local/includeIf 后的值） */
    fun readEffectiveUserEmail(project: Project, repository: GitRepository): ConfigReadResult =
        readEffective(project, repository, "user.email")

    /** 读取仓库最终生效的任意配置键（未设置 → Value(null)） */
    fun readEffective(project: Project, repository: GitRepository, key: String): ConfigReadResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.CONFIG).apply {
            // 预期内的失败（键未设置）不写入 idea.log 的错误通道，降低噪音
            setSilent(true)
            addParameters(key)
        }
        val result = Git.getInstance().runCommand(handler)
        return when {
            result.success() -> {
                val value = result.getOutputAsJoinedString().trim()
                ConfigReadResult.Value(value.ifEmpty { null })
            }
            // `git config <key>` 在键未设置时退出码为 1 且无输出——这是"未设置"而非"执行失败"
            result.exitCode == 1 -> ConfigReadResult.Value(null)
            else -> {
                LOG.info("git config $key failed in ${repository.root.path}: ${result.errorOutputAsJoinedString}")
                ConfigReadResult.ExecError(result.errorOutputAsJoinedString.ifBlank { "exit=${result.exitCode}" })
            }
        }
    }

    /** 写入仓库本地配置：`git config --local user.email <email>` */
    fun writeLocalUserEmail(project: Project, repository: GitRepository, email: String): ConfigWriteResult =
        writeLocal(project, repository, "user.email", email)

    /** 写入仓库本地 user.name（与邮箱拆分为独立写入，§3.3.4） */
    fun writeLocalUserName(project: Project, repository: GitRepository, name: String): ConfigWriteResult =
        writeLocal(project, repository, "user.name", name)

    /**
     * 通用本地配置写入：`git config --local <key> <value>`，写入后回读验证（§3.3.5）。
     */
    private fun writeLocal(project: Project, repository: GitRepository, key: String, value: String): ConfigWriteResult {
        val handler = GitLineHandler(project, repository.root, GitCommand.CONFIG).apply {
            setSilent(true)
            addParameters("--local", key, value)
        }
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            return ConfigWriteResult.Failure(diagnose(result))
        }
        // 回读确认确实生效，防止"返回成功但配置未落盘"的假象
        return when (val read = readEffective(project, repository, key)) {
            is ConfigReadResult.Value ->
                if (read.value?.trim() == value.trim()) {
                    ConfigWriteResult.Success
                } else {
                    ConfigWriteResult.Failure("写入后校验失败：$key 生效值为 ${read.value ?: "(未设置)"}")
                }
            is ConfigReadResult.ExecError -> ConfigWriteResult.Failure("写入后无法校验：${read.message}")
        }
    }

    /** 已知失败原因诊断，给出可读提示（§3.3.6） */
    private fun diagnose(result: GitCommandResult): String {
        val err = result.errorOutputAsJoinedString
        return when {
            err.contains("config.lock", ignoreCase = true) ->
                "存在 .git/config.lock 锁文件（可能有其他 git 进程正在运行或异常残留）"
            err.contains("Permission denied", ignoreCase = true) || err.contains("Read-only", ignoreCase = true) ->
                "仓库目录不可写（只读文件系统或权限不足）"
            err.isBlank() -> "git 退出码 ${result.exitCode}"
            else -> err
        }
    }
}
