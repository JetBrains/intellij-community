/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing.configurers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil.fileToUrl
import com.intellij.openapi.vfs.VfsUtilCore.urlToPath
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

/**
 * @author Sergey Evdokimov
 */
class MavenEncodingConfigurer : MavenModuleConfigurer() {
  override fun configure(mavenProject: MavenProject, project: Project, module: Module) {
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
      } else {
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
