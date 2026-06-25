plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "me.ele.uetool.compose"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
    }

    publishing {
        singleVariant("release")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    compileOnly(project(":uetool-base"))
    compileOnly(libs.compose.ui)
    implementation(libs.kotlin.stdlib)
}

afterEvaluate {
    publishing {
        publications {
            create<org.gradle.api.publish.maven.MavenPublication>("release") {
                from(components["release"])
            }
        }
    }
}
