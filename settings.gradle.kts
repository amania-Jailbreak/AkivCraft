pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://libraries.minecraft.net")
        maven("https://repo.viaversion.com")
    }
}

rootProject.name = "akivcraft"
include("loader-java")
