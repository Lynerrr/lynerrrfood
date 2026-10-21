plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lynerrr.food"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lynerrr.food"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.4.1"

        // Where the app checks for a newer packaged-products database, e.g. a GitHub raw link to
        // products/products.json. Set in gradle.properties or with -PproductsManifestUrl=...; empty hides the button.
        val manifestUrl = (project.findProperty("productsManifestUrl") as String?).orEmpty()
        buildConfigField("String", "PRODUCTS_MANIFEST_URL", "\"$manifestUrl\"")

        // GitHub repository ("owner/repo") whose releases the app checks for updates on start; empty turns it off.
        // Set updatesRepo in gradle.properties; scripts/publish-release.sh publishes there.
        val updatesRepo = (project.findProperty("updatesRepo") as String?).orEmpty()
        buildConfigField("String", "UPDATES_REPO", "\"$updatesRepo\"")
        // Only for testing against a local stand-in (debug builds allow http://localhost): -PupdatesApi=http://localhost:8765
        val updatesApi = (project.findProperty("updatesApi") as String?) ?: "https://api.github.com"
        buildConfigField("String", "UPDATES_API", "\"$updatesApi\"")
    }

    // The app's own key, kept out of the repo: lynerrrfoodReleaseKeystore / lynerrrfoodReleasePassword come from
    // ~/.gradle/gradle.properties (or ORG_GRADLE_PROJECT_* variables in CI).
    val releaseKeystore = project.findProperty("lynerrrfoodReleaseKeystore") as String?
    val releasePassword = project.findProperty("lynerrrfoodReleasePassword") as String?
    if (releaseKeystore != null && releasePassword != null) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystore)
            storePassword = releasePassword
            keyAlias = "lynerrrfood"
            keyPassword = releasePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Left unsigned here: scripts/sign-release-apk.sh signs it with the release key plus the rotation proof
            // from the old debug key (signing/lineage.bin), which Gradle can't do, so earlier installs accept it.
            signingConfig = null
        }
        debug {
            // Same key as releases, so debug builds install over a release (and the other way round).
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Duplicate metadata from the Anthropic SDK's JVM dependencies.
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("org.jsoup:jsoup:1.23.2")

    // Barcode scanning through Google Play services: its own camera UI, no camera permission needed.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // AI photo estimates (Claude) and photo orientation handling.
    implementation("com.anthropic:anthropic-java:2.72.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    testImplementation("junit:junit:4.13.2")
}
