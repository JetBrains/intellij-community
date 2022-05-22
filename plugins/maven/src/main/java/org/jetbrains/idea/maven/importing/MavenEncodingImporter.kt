// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.fileToUrl
import com.intellij.openapi.vfs.VfsUtilCore.urlToPath
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

/**
 * @author Sergey Evdokimov
 */
class MavenEncodingImporter : MavenImporter("", "") {

  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return true;
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {
    configure(mavenProject, module.project)
  }

  private fun configure(mavenProject: MavenProject, project: Project) {
    val encodingCollector = EncodingCollector(project)

    ReadAction.compute<Unit, Throwable> {
      fillSourceEncoding(mavenProject, encodingCollector)
    }

    ReadAction.compute<Unit, Throwable> {
      fillResourceEncoding(project, mavenProject, encodingCollector)
    }

    encodingCollector.applyCollectedInfo()
  }

  class EncodingCollector(project: Project) {
    private val newPointerMappings = LinkedHashMap<VirtualFilePointer, Charset>()
    private val oldPointerMappings = LinkedHashMap<VirtualFilePointer, Charset>()
    private val encodingManager = (EncodingProjectManager.getInstance(project) as EncodingProjectManagerImpl)

    fun processDir(directory: String, charset: Charset) {
      val dirVfile = LocalFileSystem.getInstance().findFileByIoFile(File(directory))
      val pointer = if (dirVfile != null) {
        service<VirtualFilePointerManager>().create(dirVfile, encodingManager, null)
      }
      else {
        service<VirtualFilePointerManager>().create(fileToUrl(File(directory).absoluteFile), encodingManager, null)
      }
      newPointerMappings[pointer] = charset
      encodingManager.allPointersMappings.forEach {
        val filePointer = it.key
        if (FileUtil.isAncestor(directory, urlToPath(filePointer.url), false)
            || newPointerMappings.containsKey(filePointer)) {
          newPointerMappings[filePointer] = charset
          oldPointerMappings.remove(filePointer)
        }
        else {
          oldPointerMappings[filePointer] = it.value
        }
      }
    }

    fun applyCollectedInfo() {
      if (newPointerMappings.isEmpty()) {
        return
      }

      val pointerMapping = newPointerMappings + oldPointerMappings

      ApplicationManager.getApplication().invokeAndWait {
        encodingManager.setPointerMapping(pointerMapping)
      }
    }
  }

  private fun fillResourceEncoding(project: Project,
                                   mavenProject: MavenProject,
                                   encodingCollector: EncodingCollector) {
    mavenProject.getResourceEncoding(project)?.let(this::getCharset)?.let { charset ->
      mavenProject.resources.map { it.directory }.forEach { encodingCollector.processDir(it, charset) }
    }
  }

  private fun fillSourceEncoding(mavenProject: MavenProject,
                                 encodingCollector: EncodingCollector) {
    mavenProject.sourceEncoding?.let(this::getCharset)?.let { charset ->
      mavenProject.sources.forEach { encodingCollector.processDir(it, charset) }
    }
  }

  private fun getCharset(name: String): Charset? {
    try {
      return Charset.forName(name)
    }
    catch (e: UnsupportedCharsetException) {
      MavenLog.LOG.warn("Charset ${name} is not supported")
      return null
    }
  }
}