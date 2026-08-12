import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val debugApiBaseUrl = providers.gradleProperty("foodmind.debugApiBaseUrl")
    .orElse(providers.environmentVariable("FOODMIND_API_BASE_URL"))
    // 10.0.2.2 is the Android Emulator's route to the development host.
    .orElse("http://10.0.2.2:8080/api/v1/")

val releaseApiBaseUrl = providers.environmentVariable("FOODMIND_API_BASE_URL")
    .orElse(providers.gradleProperty("foodmind.apiBaseUrl"))
    .orElse("https://api.foodmind.example/api/v1/")

val validateReleaseApiBaseUrl by tasks.registering {
    group = "verification"
    description = "Rejects missing, insecure, or placeholder Android release API origins."
    doLast {
        val value = releaseApiBaseUrl.get()
        val uri = runCatching { URI(value) }
            .getOrElse { throw GradleException("FOODMIND_API_BASE_URL is not a valid URI.", it) }
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.host.endsWith(".example") || !uri.path.endsWith("/api/v1/")) {
            throw GradleException(
                "Release FOODMIND_API_BASE_URL must use HTTPS, must not be an example domain, and must end with /api/v1/.",
            )
        }
    }
}

android {
    namespace = "com.foodmind.foodmind_android"
    // core-ktx 1.19.0 requires API 37 during AAR metadata validation.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.foodmind.foodmind_android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("String", "FOODMIND_API_BASE_URL", "\"${debugApiBaseUrl.get()}\"")
        }
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "FOODMIND_API_BASE_URL", "\"${releaseApiBaseUrl.get()}\"")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    debugImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.matching { it.name in setOf("preReleaseBuild", "assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(validateReleaseApiBaseUrl)
}
