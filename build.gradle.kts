import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import kotlin.jvm.java

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.comLightningkiteTestingManual)
    alias(libs.plugins.vanniktechPublishing)
    alias(libs.plugins.dokka)
    signing
    `version-catalog`
}

repositories {
    mavenLocal()
    maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
    mavenCentral()
    google()
    maven("https://jitpack.io")
}


kotlin {
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
    val nativeCinteropOptIns: KotlinNativeTarget.() -> Unit = {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlinx.cinterop.BetaInteropApi")
            freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
        }
    }
    iosX64(nativeCinteropOptIns)
    iosArm64(nativeCinteropOptIns)
    iosSimulatorArm64(nativeCinteropOptIns)
    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useFirefox()
                }
            }
        }
    }

    explicitApi()
    // `lk` compares a fresh dump with the last release's to choose the next version, so no dumps are committed or checked.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        freeCompilerArgs.add("-Xreturn-value-checker=check")
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

tasks.matching { it.name == "checkKotlinAbi" }.configureEach { enabled = false }

// Group and version come from gradle.properties.  `lk` (in metawork) is the only thing that edits the version.
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    // Dokka is slow, so the javadoc jar is built only on request.
    configureBasedOnAppliedPlugins(sourcesJar = true, javadocJar = providers.gradleProperty("withDocs").isPresent)
    pom {
        name.set(project.name)
        description.set("A lightweight reactivity platform for Kotlin Multiplatform")
        url.set("https://lightningkite-maven.s3.amazonaws.com/com/lightningkite/reactive/$version/docs/index.html")
        scm {
            connection.set("scm:git:https://github.com/lightningkite/reactive.git")
            developerConnection.set("scm:git:https://github.com/lightningkite/reactive.git")
            url.set("https://github.com/lightningkite/reactive")
        }
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer { id.set("LightningKiteJoseph"); name.set("Joseph Ivie"); email.set("joseph@lightningkite.com") }
            developer { id.set("bjsvedin"); name.set("Brady Svedin"); email.set("brady@lightningkite.com") }
            developer { id.set("iHoonter"); name.set("Hunter"); email.set("Hunter@lightningkite.com") }
        }
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey").orNull
    if (signingKey != null) useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
}
tasks.withType<Sign>().configureEach {
    val hasKey = providers.gradleProperty("signingKey").isPresent
    onlyIf("a signing key is configured") { hasKey }
}

dependencies {
    dokkaPlugin("com.lightningkite:dokka-plugin-hide-optin:0.0.4")
}
dokka {
    dokkaSourceSets.configureEach {
        sourceRoots.map { it.resolve("module.md") }.firstOrNull { it.exists() }?.let { includes.from(it) }
        sourceLink {
            localDirectory.set(file("src/$name/kotlin"))
            remoteUrl.set(uri("https://github.com/lightningkite/reactive/tree/master/"))
            remoteLineSuffix.set("#L")
        }
    }
}

// The catalog downstream repos pin: this repo's modules at the versions being published.
catalog {
    versionCatalog {
        library("reactive", "$group:reactive:$version")
    }
}
publishing {
    repositories {
        maven { name = "staging"; url = uri(layout.buildDirectory.dir("staging-repo")) }
    }
    publications.create<MavenPublication>("catalog") {
        from(components["versionCatalog"])
    }
}
// After the publishing plugin has given every publication the module's coordinates, which aren't the catalog's.
afterEvaluate {
    publishing.publications.named<MavenPublication>("catalog") {
        groupId = "com.lightningkite.stack"
        artifactId = "reactive-catalog"
        version = providers.gradleProperty("catalogVersion").getOrElse("unspecified")
    }
}

plugins.withType(YarnPlugin::class.java) {
    the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.NONE
    the<YarnRootExtension>().reportNewYarnLock = false
    the<YarnRootExtension>().yarnLockAutoReplace = true
}