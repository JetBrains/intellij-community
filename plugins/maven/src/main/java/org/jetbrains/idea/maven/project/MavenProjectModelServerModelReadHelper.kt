// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.model.MavenParent
import org.jetbrains.idea.maven.server.MavenServerConfigUtil
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.Properties
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.isRegularFile


open class MavenProjectModelServerModelReadHelper(protected val myProject: Project) : MavenProjectModelReadHelper {
  override fun filterModules(modules: List<String>, mavenModuleFile: VirtualFile): List<String> {
    return modules;
  }

  private fun needInterpolate(mavenId: MavenId?): Boolean {
    if (mavenId == null) return false
    return needInterpolate(mavenId.artifactId) || needInterpolate(mavenId.groupId) || needInterpolate(mavenId.version)
  }

  private fun needInterpolate(mavenId: String?): Boolean {
    return mavenId?.contains($$"${") ?: false
  }

  private fun interpolateString(property: String, properties: HashMap<String, String>): String {
    val resolved = MavenProjectModelReadHelper.resolveProperty(property, properties)
    if (resolved == null) {
      if (MavenLog.LOG.isDebugEnabled) {
        MavenLog.LOG.debug("Cannot resolve property $property, collected properties: $properties")
      }
      else {
        MavenLog.LOG.warn("Cannot resolve property $property")
      }
      return property
    }
    return resolved

  }

  @OptIn(ExperimentalContracts::class)
  private fun interpolateMavenId(
    mavenId: MavenId?,
    properties: HashMap<String, String>,
  ): MavenId? {
    contract {
      returnsNotNull() implies (mavenId != null)
      returns(null) implies (mavenId == null)
    }
    if (mavenId == null) return null
    return MavenId(
      mavenId.groupId?.let { interpolateString(it, properties) },
      mavenId.artifactId?.let { interpolateString(it, properties) },
      mavenId.version?.let { interpolateString(it, properties) }
    )
  }

  override suspend fun interpolate(mavenModuleFile: VirtualFile, model: MavenModel): MavenModel {
    if (!needInterpolate(model.mavenId) && !needInterpolate(model.parent?.mavenId)) {
      return model
    }
    val properties = HashMap<String, String>()
    val basedir = MavenUtil.getBaseDir(mavenModuleFile)
    if (basedir.isRegularFile()) {
      properties.putAll(MavenServerConfigUtil.getMavenAndJvmConfigPropertiesForBaseDir(basedir))
    }

    model.properties.keys.forEach { k ->
      properties[k.toString()] = model.properties.getProperty(k.toString())
    }


    return model.copy().apply {
      mavenId = interpolateMavenId(model.mavenId, properties)
      parent = MavenParent(interpolateMavenId(model.parent?.mavenId, properties), parent?.relativePath)
    }
  }


  override suspend fun assembleInheritance(parentModel: MavenModel, model: MavenModel, file: VirtualFile): MavenModel {
    //we assemble only properties for this stage
    val properties = Properties()
    properties.putAll(parentModel.properties)
    properties.putAll(model.properties)
    model.setProperties(properties)
    return model
  }
}
