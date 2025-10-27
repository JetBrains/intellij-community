package com.intellij.ide.starter.ide

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

abstract class IdeDistribution {
  abstract fun installIde(unpackDir: Path, executableFileName: String): InstalledIde

  companion object {
    /** @return Product code and build number */
    fun readProductCodeAndBuildNumberFromBuildTxt(buildTxtPath: Path): Pair<String, String> {
      require(buildTxtPath.isRegularFile()) { "Cannot find build.txt file" }

      val (productCode, build) = Files.readString(buildTxtPath).trim().split('-', limit = 2)
      return Pair(productCode, build)
    }
  }
}