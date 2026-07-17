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

    buildFeatures {
        // AGP 8 stopped generating BuildConfig by default; the shell reads
        // BuildConfig.DEBUG to decide whether web-view inspection is allowed,
        // which is the one thing that must differ between the two variants.
        buildConfig = true
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
            // Prebuilt decoder libraries, fetched from the published package by
            // scripts/pull-decoder.js. They are a build input, not source, and
            // are never committed — see docs/CONTRACT.md.
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // One universal artifact, every ABI in it. Splitting per ABI would multiply
    // the templates Studio ships and the ones it must choose between, to save
    // ~25 KB in a package that carries a whole game.
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
        // The decoder libraries must stay compressed: the repacker rewrites the
        // archive with 4-byte alignment, and an uncompressed .so would then need
        // page alignment it will not get (see extractNativeLibs in the manifest).
        jniLibs.useLegacyPackaging = true
    }
}

// Zero third-party dependencies, deliberately: every added library risks
// pulling a <provider> (whose authority embeds the placeholder application id)
// into the merged manifest, and grows a template that ships with every game.
// The CI asserts this block stays empty.
dependencies {
}
