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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
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
    val newMap = LinkedHashMap<VirtualFile, Charset>()
    val leaveAsIsMap = LinkedHashMap<VirtualFile, Charset>()
    val projectManagerImpl = (EncodingProjectManager.getInstance(project) as EncodingProjectManagerImpl)

    ReadAction.compute<Unit, Throwable> {
      fillSourceEncoding(mavenProject, newMap, leaveAsIsMap, projectManagerImpl)
    }

    ReadAction.compute<Unit, Throwable> {
      fillResourceEncoding(project, mavenProject, newMap, leaveAsIsMap, projectManagerImpl)
    }

    if (newMap.isEmpty()) {
      return
    }

    newMap.putAll(leaveAsIsMap)

    ApplicationManager.getApplication().invokeAndWait {
      projectManagerImpl.setMapping(newMap)
    }
  }

  private fun fillResourceEncoding(project: Project,
                                   mavenProject: MavenProject,
                                   newMap: LinkedHashMap<VirtualFile, Charset>,
                                   leaveAsIsMap: LinkedHashMap<VirtualFile, Charset>,
                                   projectManagerImpl: EncodingProjectManagerImpl) {
    mavenProject.getResourceEncoding(project)?.let(this::getCharset)?.let { charset ->
      mavenProject.resources.forEach { resource ->
        val dirVfile = LocalFileSystem.getInstance().findFileByIoFile(File(resource.directory)) ?: return
        newMap[dirVfile] = charset
        projectManagerImpl.allMappings.forEach {
          if (FileUtil.isAncestor(resource.directory, it.key.path, false)) {
            newMap[it.key] = charset
          }
          else {
            leaveAsIsMap[it.key] = it.value
          }
        }
      }
    }
  }

  private fun fillSourceEncoding(mavenProject: MavenProject,
                                 newMap: LinkedHashMap<VirtualFile, Charset>,
                                 leaveAsIsMap: LinkedHashMap<VirtualFile, Charset>,
                                 projectManagerImpl: EncodingProjectManagerImpl) {
    mavenProject.sourceEncoding?.let(this::getCharset)?.let { charset ->
      mavenProject.sources.forEach { directory ->
        val dirVfile = LocalFileSystem.getInstance().findFileByIoFile(File(directory)) ?: return
        newMap[dirVfile] = charset
        projectManagerImpl.allMappings.forEach {
          if (FileUtil.isAncestor(directory, it.key.path, false)) {
            newMap[it.key] = charset
          }
          else {
            leaveAsIsMap[it.key] = it.value
          }
        }
      }
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
