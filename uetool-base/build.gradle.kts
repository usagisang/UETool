plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "me.ele.uetool.base"
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

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.appcompat)
    api(libs.androidx.core)
    api(libs.androidx.fragment)
    api(libs.material)
    implementation(libs.free.reflection)
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
