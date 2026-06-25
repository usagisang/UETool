plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

allprojects {
    group = "com.github.usagisang"
    version = findProperty("uetoolVersion")?.toString() ?: "1.3.5"
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
