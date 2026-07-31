package io.github.identityguard

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.GitIdentityGuardBundle"

/**
 * 插件消息包（设计文档 §6.2）。
 *
 * 所有用户可见文案统一走这里，后续可无痛切换 DynamicBundle 多语言；
 * 当前版本文案为中文定版。
 */
object GitIdentityGuardBundle : DynamicBundle(BUNDLE) {

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
