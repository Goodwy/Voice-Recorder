import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

base {
    archivesName.set("voice-recorder")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
val properties = Properties().apply {
    load(rootProject.file("local.properties").reader())
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = libs.versions.app.version.appId.get()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.libs.versions.app.version.versionName.get()
        versionCode = project.libs.versions.app.version.versionCode.get().toInt()
        vectorDrawables.useSupportLibrary = true
        setProperty("archivesBaseName", "voice-recorder-$versionCode")
        buildConfigField("String", "GOOGLE_PLAY_LICENSING_KEY", "\"${properties["GOOGLE_PLAY_LICENSE_KEY"]}\"")
        buildConfigField("String", "PRODUCT_ID_X1", "\"${properties["PRODUCT_ID_X1"]}\"")
        buildConfigField("String", "PRODUCT_ID_X2", "\"${properties["PRODUCT_ID_X2"]}\"")
        buildConfigField("String", "PRODUCT_ID_X3", "\"${properties["PRODUCT_ID_X3"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X1", "\"${properties["SUBSCRIPTION_ID_X1"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X2", "\"${properties["SUBSCRIPTION_ID_X2"]}\"")
        buildConfigField("String", "SUBSCRIPTION_ID_X3", "\"${properties["SUBSCRIPTION_ID_X3"]}\"")
        buildConfigField("String", "SUBSCRIPTION_YEAR_ID_X1", "\"${properties["SUBSCRIPTION_YEAR_ID_X1"]}\"")
        buildConfigField("String", "SUBSCRIPTION_YEAR_ID_X2", "\"${properties["SUBSCRIPTION_YEAR_ID_X2"]}\"")
        buildConfigField("String", "SUBSCRIPTION_YEAR_ID_X3", "\"${properties["SUBSCRIPTION_YEAR_ID_X3"]}\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("prepaid")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = project.libs.versions.app.build.kotlinJVMTarget.get()
    }

    namespace = libs.versions.app.version.appId.get()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
    }

    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
}

dependencies {
    implementation(libs.eventbus)
    implementation(libs.audiorecordview)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.tandroidlame)
    implementation(libs.autofittextview)

    //Goodwy
    implementation(libs.goodwy.commons)
    implementation(libs.behavio.rule)
    implementation(libs.bundles.lifecycle)
    implementation(libs.rx.animation)
    implementation(libs.rx.java)
    implementation(libs.swipe.action)
}
