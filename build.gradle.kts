import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "io.github.2doze"
version = "0.1.0"

repositories {
    intellijPlatform {
        defaultRepositories()
    }
    // Plugin Verifier 的 IDE 发行版仓库(与 Xinyuan-Java-Guidelines 同源)
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-plugin-verifier") }
    mavenCentral()
}

dependencies {
    intellijPlatform {
        // 编译基线:2024.2(242)——GitHub 插件认证/请求 API 重构后的首个版本,
        // 即 GHCompatibilityUtil.getOrRequestToken + Factory.create(server, token) 的起点。
        // 使用声明的最低支持版本编译,提前暴露误用新版 API 的问题。
        intellijIdeaCommunity("2024.2")
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.plugins.github")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier()
    }
    // 纯逻辑单测使用 JUnit4;Plugin 2.x 的 testFramework 不再默认传递该依赖
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginVerification {
        ides {
            // 覆盖 sinceBuild(2024.2)到 2026 的多个 IDE 版本;CE/UE 对捆绑插件的解析可能不同。
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2")
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2025.3.3")
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2026.1")
        }
    }
}

kotlin {
    // 2024.2+ 运行时为 JBR 21,编译工具链随之对齐
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }

    prepareJarSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        // 242 = 2024.2,新版 GitHub 插件 API 的首个兼容版本;2023.1 兼容已放弃
        sinceBuild.set("242")
    }
}
