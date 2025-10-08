import org.apache.tools.ant.taskdefs.condition.Os
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.div

plugins {
  id("com.jetbrains.python.envs") version "0.0.31"
}

val pythonsDirectory: Path = layout.buildDirectory.file("pythons").get().asFile.toPath()
val defaultArchiveWindows = "https://packages.jetbrains.team/files/p/py/python-archives-windows/"
val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
val pythonExecutableName = if (isWindows) "python.exe" else "bin/python"
val defaultPackages = listOf("teamcity-messages")

envs {
  bootstrapDirectory = pythonsDirectory.toFile()
  zipRepository = URL(System.getenv().getOrDefault("PYCHARM_ZIP_REPOSITORY", defaultArchiveWindows))
  shouldUseZipsFromRepository = isWindows

  fun testHelpers(pythonName: String, pythonVersion: String) {
    python(pythonName, pythonVersion, defaultPackages)

    val pythonExecutable = pythonsDirectory / pythonName / pythonExecutableName

    tasks.register<Exec>("Tests for Python ${pythonVersion}") {
      mustRunAfter("build_envs")
      environment("PYTHONPATH" to ".:..")
      commandLine(pythonExecutable, "__main__.py")
    }
  }

  testHelpers("py27_64", "2.7.18")
  testHelpers("py38_64", if (isWindows) "3.8.10" else "3.8.19")
  testHelpers("py39_64", if (isWindows) "3.9.13" else "3.9.19")
  testHelpers("py310_64", if (isWindows) "3.10.11" else "3.10.14")
  testHelpers("py311_64", "3.11.9")
  testHelpers("py312_64", "3.12.4")
  testHelpers("py313_64", "3.13.0")
}

tasks.register("all_tests") {
  dependsOn("build_envs", tasks.matching { it.name.startsWith("Tests for Python") })
}
