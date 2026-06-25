plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "me.ele.uetool.fresco"
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
    compileOnly(project(":uetool-base"))
//  implementation("me.ele:uetool-base:${libs.versions.release.get()}")
    compileOnly(libs.facebook.fresco)
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
