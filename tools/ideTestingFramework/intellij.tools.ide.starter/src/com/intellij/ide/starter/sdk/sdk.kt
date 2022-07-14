package com.intellij.ide.starter.sdk

import java.nio.file.Path
import kotlin.io.path.div

fun setupAndroidSdkToProject(projectPath: Path, androidSdkPath: Path) {
  val localPropertiesFile = projectPath / "local.properties"
  val path = androidSdkPath.toFile().absolutePath.replace("""\""", """\\""")
  localPropertiesFile.toFile().writeText("sdk.dir=${path}")
}