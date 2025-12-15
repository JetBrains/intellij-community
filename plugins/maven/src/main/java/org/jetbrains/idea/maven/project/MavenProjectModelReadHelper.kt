// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.nullize
import org.jetbrains.idea.maven.model.MavenModel

interface MavenProjectModelReadHelper {
  fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String>
  suspend fun interpolate(mavenModuleFile: VirtualFile, model: MavenModel): MavenModel
  suspend fun assembleInheritance(parentModel: MavenModel, model: MavenModel, file: VirtualFile): MavenModel

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenProjectModelReadHelper = project.service<MavenProjectModelReadHelper>()

    /**
     * Resolves properties recursively. If any resolution fails, it returns null.
     * This helps avoid a situation where the root directory is added as a source root
     * in complex cases involving variables and properties
     */
    fun resolveProperty(propertyValue: String, allProperties: HashMap<String, String>): String? {

      var value = propertyValue
      val recursionProtector = HashSet<String>()
      while (recursionProtector.add(value)) {
        val start = value.indexOf("${'$'}{")
        if (start == -1) return value
        val end = value.indexOf("}")
        if (start + 2 >= end) return null // some syntax error probably
        val variable = value.substring(start + 2, end)
        val resolvedValue = doResolveVariable(variable, allProperties) ?: return null
        if (start == 0 && end == value.length - 1) {
          value = resolvedValue
          continue
        }
        val tail = if (end == value.length - 1) {
          ""
        }
        else {
          value.substring(end + 1, value.length)
        }
        value = value.substring(0, start) + resolvedValue + tail
      }
      return value
    }

    private fun doResolveVariable(variable: String, properties: HashMap<String, String>): String? {
      properties[variable]?.let { return it }
      if (variable.startsWith("env.")) {
        val env = variable.substring(4)
        return when {
          env.isNotBlank() -> System.getenv(env).nullize(true)
          else -> null
        }
      }
      return System.getProperty(variable).nullize(true)
    }
  }
}
