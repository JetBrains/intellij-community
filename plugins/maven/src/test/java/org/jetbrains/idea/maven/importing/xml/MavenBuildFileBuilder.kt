// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.xml

class MavenBuildFileBuilder(val artifactId: String) {
  private var modelVersion: String = "4.0.0"
  private var groupId: String = "org.example"
  private var version: String = "1.0-SNAPSHOT"
  private var packaging: String? = null
  private val modules = ArrayList<Module>()

  fun withPomPackaging(): MavenBuildFileBuilder {
    packaging = "pom"
    return this
  }

  fun withModule(name: String): MavenBuildFileBuilder {
    modules.add(Module(name))
    return this
  }

  fun generate(): String {
    val builder = XmlBuilder().apply {
      attribute("xmlns", "http://maven.apache.org/POM/4.0.0")
      attribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
      attribute("xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd")
      block("project") {
        value("modelVersion", modelVersion)
        generateProjectInfo()
        generateModules()
      }
    }
    return builder.generate()
  }

  private fun XmlBuilder.generateProjectInfo() {
    value("groupId", groupId)
    value("artifactId", artifactId)
    value("version", version)
  }

  private fun XmlBuilder.generateModules() {
    packaging?.let { value("packaging", it) }
    if (modules.isEmpty()) return
    block("modules") {
      for (module in modules) {
        generateModule(module)
      }
    }
  }

  private fun XmlBuilder.generateModule(module: Module) {
    value("module", module.name)
  }

  data class Module(val name: String)
}