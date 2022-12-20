package com.intellij.mermaid.build

import org.gradle.api.Project
import org.gradle.kotlin.dsl.registering

val Project.shouldBundleSourceMaps: Boolean
  get() = (findProperty("shouldBundleSourceMaps") as? String).toBoolean()
