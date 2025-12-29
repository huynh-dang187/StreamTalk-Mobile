pluginManagement {
    repositories {
        mavenCentral() // 👈 Đưa lên đầu
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral() // 👈 Đưa lên đầu (Quan trọng nhất)
        google()
        maven { url = uri("https://jitpack.io") }
        jcenter() // Giữ lại để dự phòng
    }
}

rootProject.name = "AndroidClient"
include(":app")