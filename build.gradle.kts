import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import com.lightningkite.deployhelpers.*
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import kotlin.jvm.java

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.comLightningkiteTestingManual)
    alias(libs.plugins.vanniktechPublishing)
    alias(libs.plugins.dokka)
    signing
}

buildscript {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath(libs.lkGradleHelpers)
    }
}

group = "com.lightningkite"
version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useFirefox()
                }
            }
        }
    }

//    explicitApi = ExplicitApiMode.Warning
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.BetaInteropApi")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }
    sourceSets {
        applyDefaultHierarchyTemplate()

        val commonMain by getting {
            dependencies {
                api(libs.kotlinxCoroutines)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinxCoroutinesTesting)
                implementation(libs.comLightningkiteTestingKotlinTestManualRuntime)
            }
        }
    }
}

lkLibrary("lightningkite", "reactive") {
    description.set("A lightweight reactivity platform for Kotlin Multiplatform")
}
plugins.withType(YarnPlugin::class.java) {
    the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.NONE
    the<YarnRootExtension>().reportNewYarnLock = false
    the<YarnRootExtension>().yarnLockAutoReplace = true
}