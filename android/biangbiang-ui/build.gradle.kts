plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "dev.veeso.biangbiangui"
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
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.veeso.BiangBiangUI"
                artifactId = "biangbiang-ui"
                version = project.version.takeIf { it != "unspecified" }?.toString() ?: "0.2.0"
            }
        }
    }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.text.recognition.chinese)
    implementation(libs.text.recognition)
    implementation(libs.text.recognition.japanese)
    implementation(libs.text.recognition.korean)
    implementation(libs.translate)
    implementation(libs.play.review.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
