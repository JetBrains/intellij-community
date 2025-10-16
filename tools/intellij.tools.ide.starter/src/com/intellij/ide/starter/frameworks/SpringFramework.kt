package com.intellij.ide.starter.frameworks

import com.intellij.ide.starter.ide.IDETestContext
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeText

class SpringFramework(testContext: IDETestContext) : Framework(testContext) {
  fun configureSpringCheck(): SpringFramework {
    val miscXml = testContext.resolvedProjectHome.resolve(".idea").resolve("misc.xml")
    if (!miscXml.exists()) return this

    val finalConfig = StringBuilder(miscXml.readText().length)
    miscXml.useLines { lines ->
      lines.forEach { line ->
        finalConfig.append(line.replace("  <component name=\"FrameworkDetectionExcludesConfiguration\">",
                                        "  <component name=\"FrameworkDetectionExcludesConfiguration\">\n" +
                                        "    <type id=\"Spring\" />"))
        finalConfig.append("\n")
      }
    }
    miscXml.writeText(finalConfig)

    return this
  }
}