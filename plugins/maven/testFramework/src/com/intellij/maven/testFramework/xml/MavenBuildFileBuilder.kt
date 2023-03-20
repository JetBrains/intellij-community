// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.xml

import org.jetbrains.idea.maven.utils.MavenArtifactScope

class MavenBuildFileBuilder(val artifactId: String) {
  private var modelVersion: String = "4.0.0"
  private var groupId: String = "org.example"
  private var version: String = "1.0-SNAPSHOT"
  private var packaging: String? = null
  private val modules = ArrayList<Module>()
  private val dependencies = ArrayList<Dependency>()
  private val properties = ArrayList<Property>()
  private val plugins = ArrayList<Plugin>()

  fun withPomPackaging(): MavenBuildFileBuilder {
    packaging = "pom"
    return this
  }

  fun withModule(name: String): MavenBuildFileBuilder {
    modules.add(Module(name))
    return this
  }

  fun withDependency(groupId: String, artifactId: String, version: String, scope: MavenArtifactScope? = null): MavenBuildFileBuilder {
    dependencies.add(Dependency(groupId, artifactId, version, scope))
    return this
  }

  fun withProperty(name: String, value: String): MavenBuildFileBuilder {
    properties.add(Property(name, value))
    return this
  }

  fun withPlugin(groupId: String, artifactId: String, buildAction: PluginBuilder.() -> Unit): MavenBuildFileBuilder {
    val pluginBuilder = PluginBuilder(groupId, artifactId)
    pluginBuilder.buildAction()
    plugins.add(pluginBuilder.build())
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
        generateDependencies()
        generateProperties()
        generatePlugins()
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

  private fun XmlBuilder.generateDependencies() {
    if (dependencies.isEmpty()) return
    block("dependencies") {
      for (dependency in dependencies) {
        generateDependency(dependency)
      }
    }
  }

  private fun XmlBuilder.generateDependency(dependency: Dependency) {
    block("dependency") {
      value("groupId", dependency.groupId)
      value("artifactId", dependency.artifactId)
      value("version", dependency.version)
      dependency.scope?.let { value("scope", it.name) }
    }
  }

  private fun XmlBuilder.generateProperties() {
    if (properties.isEmpty()) return
    block("properties") {
      for (property in properties) {
        generateProperty(property)
      }
    }
  }

  private fun XmlBuilder.generateProperty(property: Property) {
    value(property.name, property.value)
  }

  private fun XmlBuilder.generatePlugins() {
    if (plugins.isEmpty()) return
    block("build") {
      block("plugins") {
        for (plugin in plugins) {
          generatePlugin(plugin)
        }
      }
    }
  }

  private fun XmlBuilder.generatePlugin(plugin: Plugin) {
    block("plugin") {
      value("groupId", plugin.groupId)
      value("artifactId", plugin.artifactId)
      plugin.version?.let { value("version", it) }
      if (plugin.attributes.isEmpty()) return@block
      block("configuration") {
        for (attribute in plugin.attributes) {
          value(attribute.name, attribute.value)
        }
      }
    }
  }

  data class Module(val name: String)

  data class Dependency(val groupId: String, val artifactId: String, val version: String, val scope: MavenArtifactScope?)

  data class Property(val name: String, val value: String)

  data class Plugin(val groupId: String,
                    val artifactId: String,
                    val version: String?,
                    val attributes: List<Attribute> = emptyList())

  data class Attribute(val name: String, val value: String)

  class PluginBuilder(val groupId: String, val artifactId: String) {
    private var version: String? = null
    private val attributes = mutableListOf<Attribute>()

    fun version(version: String): PluginBuilder {
      this.version = version
      return this
    }

    fun attribute(name: String, value: String): PluginBuilder {
      attributes.add(Attribute(name, value))
      return this
    }

    fun build() = Plugin(groupId, artifactId, version, attributes)
  }
}