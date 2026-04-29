plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // KSP runs annotation processors on Kotlin source — needed for Room.
    id("com.google.devtools.ksp")
}

android {
    // The application's namespace. Used as the default package for the generated R class
    // and BuildConfig, and as the manifest's package if no explicit one is set.
    namespace = "com.example.projectophelia"

    // The Android SDK version we compile against. Higher = more APIs available.
    compileSdk = 35

    defaultConfig {
        // The unique identifier for the app on a device / in the Play Store.
        applicationId = "com.example.projectophelia"
        // Lowest Android version we support. 26 = Android 8.0 (Oreo).
        minSdk = 26
        // The Android version we've tested against and target. Affects runtime behavior
        // (e.g. permission models, background restrictions).
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Shrinking/obfuscation off for now to keep builds simple. Turn on for prod.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Source-level Java target. 17 matches Android Studio's bundled JDK.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // The bytecode level Kotlin emits. Should match compileOptions above.
        jvmTarget = "17"
    }

    // Enables generated `XxxBinding` classes per layout XML, replacing findViewById.
    // For activity_main.xml we get ActivityMainBinding with typed view properties.
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ---- Core AndroidX & Material ----
    // Kotlin extensions to AndroidX core (extension functions, KTX-style APIs).
    implementation("androidx.core:core-ktx:1.13.1")
    // AppCompat: backwards-compatible Activity, theming, etc.
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Material Components: FAB, Toolbar, Snackbar, TextInputLayout, etc.
    implementation("com.google.android.material:material:1.12.0")
    // ConstraintLayout: flexible flat layout used in our XML.
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // RecyclerView: efficient list/grid widget for the notes list.
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Activity-ktx: viewModels() delegate, registerForActivityResult, etc.
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Fragment-ktx: needed transitively for some lifecycle helpers; kept explicit for clarity.
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // ---- Lifecycle / ViewModel ----
    // ViewModel + KTX (viewModelScope coroutine support).
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    // lifecycle-runtime-ktx gives us repeatOnLifecycle for safe Flow collection.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ---- Room (SQLite ORM) ----
    // The Room runtime — entity scanning, query execution.
    implementation("androidx.room:room-runtime:2.6.1")
    // Kotlin extensions: suspend DAO methods + Flow return types.
    implementation("androidx.room:room-ktx:2.6.1")
    // Annotation processor that generates DAO/Database implementations at build time.
    // Run via KSP so it's faster than the older kapt path.
    ksp("androidx.room:room-compiler:2.6.1")
}
