plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val appVersionName = providers.gradleProperty("appVersionName").orElse("22.34.10").get()
val appVersionParts = appVersionName.split(".").map { it.toIntOrNull() ?: error("appVersionName debe ser numérico, por ejemplo 22.34.10") }
require(appVersionParts.size in 2..3) { "appVersionName debe tener dos o tres componentes numéricos" }
val appVersionCode = appVersionParts[0] * 100000 + (appVersionParts.getOrElse(1) { 0 }) * 100 + appVersionParts.getOrElse(2) { 0 }
val mediaResolverUrl = providers.gradleProperty("mediaResolverUrl")
    .orElse(providers.environmentVariable("MILTVPLUS_RESOLVER_URL"))
    .orElse("https://miltvplus-media-resolver.onrender.com")
    .get().trim().trimEnd('/')

android {
    namespace = "com.matuucrea.miltvplus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.matuucrea.miltvplus"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"https://rbnuxapfsnaabrzuxvdy.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_tmBU-IB7P0db0w7RG9TMEw_GD754hb7\"")
        buildConfigField("String", "MEDIA_RESOLVER_URL", "\"$mediaResolverUrl\"")
    }
    // Release signing is supplied only by protected CI secrets, never committed to source.
    signingConfigs {
        create("release") {
            val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!releaseStorePath.isNullOrBlank()) {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    // SupabaseCatalogRepository reads SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY
    // from the generated com.matuucrea.miltvplus.BuildConfig class.
    // Keep this enabled explicitly: AGP 8+ does not generate BuildConfig by default.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
