plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.narraleaf.shell"
    compileSdk = 34

    defaultConfig {
        // Placeholder identity. The repacker rewrites every one of these; the
        // CI asserts they are present verbatim in the built template.
        applicationId = "com.narraleaf.shell.placeholder"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.0"
    }

    buildTypes {
        release {
            // Never minify or shrink: the template's manifest, resource table
            // and entry names are the repacker's patch targets, and they must
            // stay exactly where the CI asserted them.
            isMinifyEnabled = false
            isShrinkResources = false
            // Unsigned on purpose — the template ships unsigned and Studio
            // signs the repacked artifact.
            signingConfig = null
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
            versionNameSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    // A single ABI-independent artifact: the shell is pure Java/Kotlin + the
    // system WebView, so there is nothing arch-specific to split on.
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

// Zero third-party dependencies, deliberately: every added library risks
// pulling a <provider> (whose authority embeds the placeholder application id)
// into the merged manifest, and grows a template that ships with every game.
// The CI asserts this block stays empty.
dependencies {
}
