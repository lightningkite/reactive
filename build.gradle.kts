import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import com.lightningkite.deployhelpers.*
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

group = "com.lightningkite"
version = "1.0-SNAPSHOT"

buildscript {
    repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.lightningkite:lk-gradle-helpers:1.2.3")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.comLightningkiteTestingManual)
    alias(libs.plugins.vanniktechMaven)
//    id("org.jetbrains.dokka")
    signing
}

val lk = project.lk {
    kotlinTestManualPlugin()
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
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
                api(lk.jsOptimizedConstructs(1))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0-RC.2")
                implementation(lk.kotlinTestManualRuntime())
            }
        }
    }
}

mavenPublishing {
    // publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(group.toString(), name, version.toString())
    pom {
        name.set("Readable")
        description.set("A lightweight reactivity platform for Kotlin Multiplatform")
        github("lightningkite", "readable")

        licenses {
            mit()
        }

        developers {
            joseph()
            brady()
            developer {
                id.set("shanelk")
                name.set("Shane Thompson")
                email.set("shane@lightningkite.com")
            }
        }
    }

}