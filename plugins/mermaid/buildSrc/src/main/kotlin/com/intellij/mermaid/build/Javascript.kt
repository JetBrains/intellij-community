package com.intellij.mermaid.build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.registering
import java.io.File

val Project.shouldBundleSourceMaps: Boolean
  get() = findBooleanProperty("shouldBundleSourceMaps")

fun File.isSourceMap(): Boolean {
  return extension == "map"
}
