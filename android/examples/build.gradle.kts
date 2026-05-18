plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.veeso.biangbiangui.examples"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 } }

dependencies {
    implementation(project(":biangbiang-ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.kotlinx.coroutines.play.services)
    // pinyin4j: example-only Mandarin romaniser. Mirrors iOS where
    // CFStringTransform lives in the example, never the library.
    implementation(libs.pinyin4j)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
