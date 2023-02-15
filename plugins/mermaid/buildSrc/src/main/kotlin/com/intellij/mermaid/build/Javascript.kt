package com.intellij.mermaid.build

import org.gradle.api.Project
import java.io.File

val Project.shouldBundleFullSourceMaps: Boolean
  get() = !isAutomatedBuild

fun File.isSourceMap(): Boolean {
  return extension == "map"
}
