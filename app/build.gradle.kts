plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.midterm.petfocus"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.midterm.petfocus"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // 1. Thư viện hoạt ảnh Lottie cho thú cưng
    implementation("com.airbnb.android:lottie:6.4.0")

    // 2. Thư viện chuyển Tab màn hình (Navigation Component)
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // 3. Thư viện cơ sở dữ liệu Room Database (lưu task và pet)
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // 4. ViewModel + Fragment KTX (by viewModels, viewModelScope, lifecycleScope)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // 5. WorkManager: chạy nền định kỳ để gửi thông báo nhắc nhở
    implementation("androidx.work:work-runtime-ktx:2.11.2")
}