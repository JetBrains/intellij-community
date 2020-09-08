package com.intellij.space.plugins.pipelines.services

import circlet.pipelines.config.api.ScriptConfig
import circlet.pipelines.config.api.parseProjectConfig
import circlet.pipelines.config.api.printJson
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
  fun load(): ScriptConfig? {

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
        reader.readLine().parseProjectConfig()
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

  fun save(config: ScriptConfig) {
    val path = getCacheFile(project)
    path.safeOutputStream().use {
      it.write(config.printJson().toByteArray(Charsets.UTF_8))
    }
  }

}
