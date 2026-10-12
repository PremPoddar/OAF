pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MuPDF, from its makers; only their packages are fetched from here.
        maven("https://maven.ghostscript.com") {
            content { includeGroup("com.artifex.mupdf") }
        }
    }
}

rootProject.name = "OAF"
include(":app")
