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


        // Add the JitPack repository
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add the Onyx repository
        maven("http://repo.boox.com/repository/maven-public/") {
            // This is how you set allowInsecureProtocol in Kotlin DSL
            // It's needed because the URL uses http instead of https
            isAllowInsecureProtocol = true
            // Or alternatively, less common but also works:
            // metadataSources {
            //     maven()
            //     artifact() // If needed, but often just maven() is enough for standard repos
            // }
            // credentials { // If credentials were required
            //     username = "user"
            //     password = "password"
            // }
        }

    }
}

rootProject.name = "notes"
include(":app")
 