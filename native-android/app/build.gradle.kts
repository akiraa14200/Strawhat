plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val supabaseUrl = providers.gradleProperty("SUPABASE_URL").orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("SUPABASE_URL").orNull?.takeIf { it.isNotBlank() }
    ?: "https://moicrqcddmftjgwjqkvz.supabase.co"
val supabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY").orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY").orNull?.takeIf { it.isNotBlank() }
    ?: "sb_publishable_XrJYTYAGNjlfPsWlP1SfUg_-yr9HBrq"

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Keep transitive AndroidX versions aligned with AGP 8.13.2 / compileSdk 36.
configurations.configureEach {
    resolutionStrategy.force(
        "androidx.core:core:1.17.0",
        "androidx.core:core-ktx:1.17.0",
        "androidx.lifecycle:lifecycle-runtime:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-ktx:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0"
    )
}

android {
    namespace = "com.rykocollection.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rykocollection.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-native"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    buildTypes {
        getByName("debug") {
            // Keep debug builds unshrunk for reliable development and diagnostics.
        }
        getByName("release") {
            // Optimized release variant. CI signs this with the temporary debug key
            // only so the APK can be installed for size/runtime testing.
            // Replace with a private upload key before Play Store publication.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Mozilla GeckoView: Firefox's Android browser engine.
    implementation("org.mozilla.geckoview:geckoview:139.0.20250609112858")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
