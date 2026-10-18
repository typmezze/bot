plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "se.kodexa.screwbot"; compileSdk = 35
 defaultConfig { applicationId = "se.kodexa.screwbot"; minSdk = 30; targetSdk = 35; versionCode = 11; versionName = "1.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
