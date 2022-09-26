// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.fileToUrl
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.io.URLUtil.urlToPath
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.utils.MavenLog
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

/**
 * @author Sergey Evdokimov
 */
@ApiStatus.Internal
class MavenEncodingConfigurator : MavenImporter("", ""), MavenWorkspaceConfigurator {
  private val PREPARED_MAPPER = Key.create<EncodingMapper>("ENCODING_MAPPER")

  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return true
  }

  override fun isMigratedToConfigurator(): Boolean {
    return true
  }

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    val allMavenProjects = context.mavenProjectsWithModules.filter { it.hasChanges() }.map { it.mavenProject }
    val mapper = mapEncodings(allMavenProjects, context.project)
    PREPARED_MAPPER.set(context, mapper)
  }

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    PREPARED_MAPPER.get(context)?.applyCollectedInfo()
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {
    mapEncodings(sequenceOf(mavenProject), module.project).applyCollectedInfo()
  }

  private fun mapEncodings(mavenProjects: Sequence<MavenProject>, project: Project): EncodingMapper {
    val encodingMapper = EncodingMapper(project)

    mavenProjects.forEach { mavenProject ->
      ReadAction.compute<Unit, Throwable> {
        fillSourceEncoding(mavenProject, encodingMapper)
      }

      ReadAction.compute<Unit, Throwable> {
        fillResourceEncoding(project, mavenProject, encodingMapper)
      }
    }

    return encodingMapper
  }

  private class EncodingMapper(project: Project) {
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
      encodingManager.setPointerMapping(pointerMapping)
    }
  }

  private fun fillResourceEncoding(project: Project,
                                   mavenProject: MavenProject,
                                   encodingMapper: EncodingMapper) {
    mavenProject.getResourceEncoding(project)?.let(this::getCharset)?.let { charset ->
      mavenProject.resources.map { it.directory }.forEach { encodingMapper.processDir(it, charset) }
    }
  }

  private fun fillSourceEncoding(mavenProject: MavenProject,
                                 encodingMapper: EncodingMapper) {
    mavenProject.sourceEncoding?.let(this::getCharset)?.let { charset ->
      mavenProject.sources.forEach { encodingMapper.processDir(it, charset) }
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