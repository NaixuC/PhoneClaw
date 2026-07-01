import org.gradle.api.tasks.Exec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val generatedAssets = providers
    .gradleProperty("phoneclaw.generatedAssets")
    .orElse("D:/temp/phoneclaw-generated-assets")
val goCache = providers.gradleProperty("phoneclaw.goCache").orElse("D:/temp/phoneclaw-go-build")
val goModCache = providers.gradleProperty("phoneclaw.goModCache").orElse("D:/temp/phoneclaw-go-mod")

val buildGoAgentArm64 by tasks.registering(Exec::class) {
    val outputDir = file("${generatedAssets.get()}/bin/arm64-v8a")
    inputs.dir(rootProject.file("core/agent-go"))
    outputs.file(file("$outputDir/agentd"))
    workingDir = rootProject.file("core/agent-go")
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    environment("GOCACHE", goCache.get())
    environment("GOMODCACHE", goModCache.get())
    doFirst { outputDir.mkdirs() }
    commandLine("go", "build", "-trimpath", "-ldflags=-s -w", "-o", file("$outputDir/agentd").absolutePath, "./cmd/agentd")
}

android {
    namespace = "com.phoneclaw.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phoneclaw.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") { assets.srcDir(generatedAssets) }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildGoAgentArm64)
}

dependencies {
    // Local modules
    implementation(project(":llama"))
    implementation(project(":mnn"))
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Kotlin
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Network
    implementation(libs.okhttp)
    // Debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
