plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    group = "me.ele"
    version = findProperty("uetoolVersion")?.toString() ?: "1.3.0"
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
