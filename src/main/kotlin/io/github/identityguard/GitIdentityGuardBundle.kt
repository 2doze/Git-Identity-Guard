package io.github.identityguard

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.GitIdentityGuardBundle"

/**
 * 插件消息包（设计文档 §6.2）。
 *
 * 所有用户可见文案统一走这里。默认资源为英文，IDE 使用简体中文区域设置时
 * DynamicBundle 会自动加载 `_zh_CN` 资源。
 */
object GitIdentityGuardBundle : DynamicBundle(BUNDLE) {

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
