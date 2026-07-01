plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0" apply false
}

val externalBuildRoot = providers
    .gradleProperty("phoneclaw.buildRoot")
    .orElse("D:/temp/phoneclaw-build")

subprojects {
    layout.buildDirectory.set(file("${externalBuildRoot.get()}/${path.trim(':').replace(':', '/')}"))
}

tasks.register<Delete>("clean") {
    delete(file(externalBuildRoot.get()))
}
