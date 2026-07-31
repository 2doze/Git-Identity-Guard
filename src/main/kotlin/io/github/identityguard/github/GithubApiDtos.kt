package io.github.identityguard.github

/**
 * `GET /user/emails` 响应条目。
 *
 * GitHub 插件 SDK 未内置该请求的 DTO，这里自行定义（F1.2）。
 * Jackson 反序列化要求：公有无参构造 + 可变属性（Kotlin 无构造参数类天然满足）。
 */
class GHUserEmailDto {
    var email: String = ""
    var primary: Boolean = false
    var verified: Boolean = false

    /** "public" / "private" / null；仅用于调试，逻辑上不依赖 */
    var visibility: String? = null
}

/**
 * `GET /user` 响应子集：只取数字 ID。
 *
 * 插件 SDK 自带的用户 DTO 只暴露 nodeId（GraphQL 全局 ID），
 * 而合成新式 noreply 邮箱需要**数字数据库 ID**（F1.1），因此自定义该 DTO。
 */
class GHUserDto {
    var id: Long = 0
    var login: String = ""
}
