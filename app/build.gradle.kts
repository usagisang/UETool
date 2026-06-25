plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "me.ele.uetool.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        create("release") {
            storeFile = file("../uetool.jks")
            keyAlias = "uetool"
            keyPassword = "uetool"
            storePassword = "uetool"
        }
    }

    defaultConfig {
        applicationId = "me.ele.uetool.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        multiDexEnabled = true
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.material)
    implementation(libs.facebook.fresco)
    implementation(libs.facebook.fresco.animated.gif)
    implementation(libs.kotlin.stdlib)
    debugImplementation(libs.leakcanary.android)

    debugImplementation(project(":uetool"))
    debugImplementation(project(":uetool-fresco"))
    debugImplementation(project(":uetool-base"))
    debugImplementation(project(":uetool-compose"))
    releaseImplementation(project(":uetool-no-op"))
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
