// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import circlet.automation.bootstrap.AutomationDslEvaluationBootstrap
import circlet.pipelines.config.idea.api.IdeaScriptConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.util.io.safeOutputStream
import libraries.klogging.logger
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

private val log = logger<ScriptKtsPersistentState>()

class ScriptKtsPersistentState(val project: Project) {

  // not for load failure
  fun load(): IdeaScriptConfig? {

    val path = getCacheFile(project)

    val fileAttributes = try {
      Files.readAttributes(path, BasicFileAttributes::class.java)
    }
    catch (ignored: FileSystemException) {
      return null
    }

    if (fileAttributes == null || !fileAttributes.isRegularFile) {
      return null
    }

    return try {
      Files.newBufferedReader(path, Charsets.UTF_8).use { reader ->
        val evalService = AutomationDslEvaluationBootstrap(log, getAutomationConfigurationWithFallback()).loadEvaluatorForIdea()
        if (evalService == null) {
          log.error("DSL evaluation service not found, cannot deserialize automation DSL model")
          return null
        }
        evalService.deserializeJsonConfig(reader.readLine())
      }
    }
    catch (e: NoSuchFileException) {
      null
    }
    catch (e: IOException) {
      log.error(e)
      null
    }
  }

  private fun getCacheFile(project: Project): Path {
    return project.getProjectCachePath("space_automation").resolve(".space.kts.dat")
  }

  fun save(config: IdeaScriptConfig) {
    val path = getCacheFile(project)
    path.safeOutputStream().use {
      it.write(config.printJson().toByteArray(Charsets.UTF_8))
    }
  }

}
