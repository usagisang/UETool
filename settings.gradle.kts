pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "UETool"

include(
    ":app",
    ":uetool",
    ":uetool-no-op",
    ":uetool-fresco",
    ":uetool-base",
    ":uetool-compose",
)
