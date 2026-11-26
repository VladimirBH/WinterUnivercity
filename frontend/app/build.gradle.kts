plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

val catalogAssets = layout.buildDirectory.dir("generated/catalogAssets")
val bundleCatalog by tasks.registering(Sync::class) {
    from(rootProject.file("../backend/catalog.json"))
    into(catalogAssets)
}

android {
    namespace = "ru.winterunivercity.throughtheages"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.winterunivercity.throughtheages"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(catalogAssets)
}

tasks.named("preBuild") { dependsOn(bundleCatalog) }

android.compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
}
