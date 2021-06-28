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

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
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
      fillSourceEncoding(mavenProject, EncodingProjectManager.getInstance(project))
      fillResourceEncoding(project, mavenProject, EncodingProjectManager.getInstance(project))
  }

  private fun fillResourceEncoding(project: Project,
                                   mavenProject: MavenProject,
                                   projectManager: EncodingProjectManager) {
    mavenProject.getResourceEncoding(project)?.let(this::getCharset)?.let { charset ->
      mavenProject.resources.forEach { resource ->
        projectManager.setEncodingByPath(File(resource.directory).absolutePath, charset)
      }
    }
  }

  private fun fillSourceEncoding(mavenProject: MavenProject,
                                 projectManager: EncodingProjectManager) {
    mavenProject.sourceEncoding?.let(this::getCharset)?.let { charset ->
      mavenProject.sources.forEach { directory ->
        projectManager.setEncodingByPath(File(directory).absolutePath, charset)
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
