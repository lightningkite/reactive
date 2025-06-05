import com.lightningkite.deployhelpers.*

group = "com.lightningkite"
version = "1.0-SNAPSHOT"

buildscript {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.lightningkite:lk-gradle-helpers:1.1.3")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:2.0.0")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.comLightningkiteTestingManual)
    alias(libs.plugins.vanniktechMaven)
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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.BetaInteropApi")
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        applyDefaultHierarchyTemplate()

        val commonMain by getting {
            dependencies {
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