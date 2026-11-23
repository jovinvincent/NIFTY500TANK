// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

tasks.register("cleanRootJunk") {
    doLast {
        val rootDir = File(".")
        rootDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val name = file.name
                if (name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".sh") || name.endsWith(".txt") ||
                    (name.endsWith(".kt") && name != "build.gradle.kts" && name != "settings.gradle.kts")
                ) {
                    println("Deleting junk file: ${file.name}")
                    file.delete()
                }
            }
        }
    }
}
