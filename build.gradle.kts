import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.pocketfm.ktlintv2"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // KtLint engine
    implementation("com.pinterest.ktlint:ktlint-rule-engine:1.5.0")
    implementation("com.pinterest.ktlint:ktlint-rule-engine-core:1.5.0")
    implementation("com.pinterest.ktlint:ktlint-cli-ruleset-core:1.5.0")
    
    // Standard rules (optional, can be disabled)
    implementation("com.pinterest.ktlint:ktlint-ruleset-standard:1.5.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
}

intellij {
    version.set("2024.1")
    type.set("IC") // IntelliJ Community Edition
    
    // Required plugins
    plugins.set(listOf(
        "org.jetbrains.kotlin"
    ))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("252.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
    
    // Copy external rule JARs to sandbox
    prepareSandbox {
        from("libs") {
            into("${intellij.pluginName.get()}/lib")
        }
    }
}

// Task to copy linter.jar from android_client
tasks.register<Copy>("copyLinterJar") {
    from("/Users/pocketfm/work/android_client/linter/build/libs/linter.jar")
    into("libs")
    rename { "pocketfm-rules.jar" }
}
