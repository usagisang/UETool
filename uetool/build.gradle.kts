plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "me.ele.uetool"
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
//  api("me.ele:uetool-base:${libs.versions.release.get()}")
    implementation(libs.androidx.appcompat)
    implementation(libs.scalpel)
    implementation(libs.treeview) {
        exclude(group = "com.android.support", module = "appcompat-v7")
    }
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
