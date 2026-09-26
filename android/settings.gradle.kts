pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "Sajag"
include(":app")

// The Unity content player, exported from the Unity editor as an Android
// library (File > Build Settings > Android > Export Project) and dropped in
// here. Commented out until that export exists so a clean clone still builds —
// which matters, because judges clone the repo.
//
// include(":unityLibrary")
// project(":unityLibrary").projectDir = file("../unity/Build/unityLibrary")
