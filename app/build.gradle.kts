plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// .env をビルド時に読み込み、BuildConfig に埋め込む
val env: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
    ?.associate { line ->
        line.substringBefore("=").trim() to line.substringAfter("=").trim().trim('"', '\'')
    }
    ?: emptyMap()

fun buildConfigString(key: String): String {
    val escaped = env[key].orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.mumeinosato.musicplayer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mumeinosato.musicplayer"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // アプリが使う値だけ埋め込む（CLOUDFLARE_R2_TOKEN はサーバー用なので含めない）
        buildConfigField("String", "SERVER_URL", buildConfigString("SERVER_URL"))
        buildConfigField("String", "R2_ENDPOINT", buildConfigString("CLOUDFLARE_R2_ENDPOINT"))
        buildConfigField("String", "R2_BUCKET", buildConfigString("CLOUDFLARE_R2_BUCKET_NAME"))
        buildConfigField("String", "R2_ACCESS_KEY_ID", buildConfigString("CLOUDFLARE_R2_ACCESS_KEY_ID"))
        buildConfigField("String", "R2_SECRET_ACCESS_KEY", buildConfigString("CLOUDFLARE_R2_SECRET_ACCESS_KEY"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}