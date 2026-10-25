import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from a git-ignored keystore.properties (see keystore.properties.sample).
// Until it's filled in, release builds are signed with the debug key so they still install.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> FileInputStream(f).use { load(it) } }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true &&
    keystoreProps.getProperty("storePassword").let { !it.isNullOrBlank() && it != "CHANGE_ME" }

android {
    namespace = "com.prempoddar.oaf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prempoddar.oaf"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
        }
        // Optimised like release but without shrinking, for trying builds on a phone: as fast
        // as release, and nothing can be stripped by mistake.
        create("fast") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/*.kotlin_module", "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/9/module-info.class", "module-info.class",
            )
        }
    }
}

dependencies {
    implementation(libs.material.color.utilities)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.subject)
    implementation(libs.mlkit.text)
    implementation(libs.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.androidx.palette)
    implementation(libs.pdfbox)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)
    implementation(libs.commons.compress)
    implementation(libs.commons.net)
    implementation(libs.arsclib)
    implementation(libs.apksig)
    implementation(libs.jsch)
    implementation(libs.smbj) { exclude(group = "org.bouncycastle", module = "bcprov-jdk18on") }
    implementation(libs.okhttp)
    implementation(libs.xz)
    implementation(libs.junrar)
    implementation(libs.zip4j)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.androidsvg)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
