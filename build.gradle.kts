plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The JetBrains CDN returns HTTP 451 for the PhpStorm archive, so the build
        // uses the local installation. If the archive is available, replace this with phpstorm("2026.2.3").
        local("/opt/devel/PhpStorm")
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        bundledModule("intellij.platform.vcs.log")
        bundledModule("intellij.platform.vcs.log.impl")
    }
    testImplementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    // Instrumentation pulls java-compiler-ant-tasks from the JetBrains CDN, which is unreachable here.
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
