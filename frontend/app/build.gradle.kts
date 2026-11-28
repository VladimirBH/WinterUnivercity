plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

val catalogAssets = layout.buildDirectory.dir("generated/catalogAssets")
val bundleCatalog by tasks.registering(Sync::class) {
    from(rootProject.file("../backend/catalog.json"))
    from(rootProject.file("../3dModeling")) {
        include("Lazarus_1862_AR.glb", "Peter_Paul_Kozhevniki_XIX_v8.glb")
        into("models")
    }
    from(rootProject.file("../backend/media")) {
        include("peter-paul-today.jpg")
        into("photos")
    }
    from(rootProject.file("../3dModeling")) {
        include("Lazarus_1862_preview.png", "Peter_Paul_Kozhevniki_XIX_v2_preview.jpg")
        into("photos")
    }
    into(catalogAssets)
}
val mapKitApiKey = providers.environmentVariable("MAPKIT_API_KEY")
    .filter { it.isNotBlank() }
    .orElse(providers.gradleProperty("MAPKIT_API_KEY"))
    .map { it.trim() }
    .getOrElse("")
val mapKitEnabled = mapKitApiKey.isNotBlank()

android {
    namespace = "ru.winterunivercity.throughtheages"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.winterunivercity.throughtheages"
        minSdk = if (mapKitEnabled) 26 else 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        if (mapKitEnabled) {
            val escapedKey = mapKitApiKey.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")
            buildConfigField("String", "MAPKIT_API_KEY", "\"$escapedKey\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main") {
        assets.srcDir(catalogAssets)
        if (mapKitEnabled) {
            java.srcDir("src/mapkit/java")
            res.srcDir("src/mapkit/res")
        } else {
            java.srcDir("src/keyless/java")
        }
    }
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("io.github.sceneview:arsceneview:4.18.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    if (mapKitEnabled) implementation("com.yandex.android:maps.mobile:4.45.0-lite")
}
