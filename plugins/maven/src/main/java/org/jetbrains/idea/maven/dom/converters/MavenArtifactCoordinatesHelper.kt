// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenId

object MavenArtifactCoordinatesHelper {
  @JvmStatic
  fun getId(context: ConvertContext): MavenId {
    return getMavenId(getCoordinates(context), context)
  }

  @JvmStatic
  fun getCoordinates(context: ConvertContext): MavenDomShortArtifactCoordinates {
    return context.invocationElement.parent as MavenDomShortArtifactCoordinates
  }

  @JvmStatic
  fun getMavenId(coords: MavenDomShortArtifactCoordinates?, context: ConvertContext): MavenId {
    if (coords is MavenDomArtifactCoordinates) {
      val version = MavenDependencyCompletionUtil.removeDummy(coords.version.stringValue)
      if (!version.isEmpty()) {
        return withVersion(coords, version)
      }
    }
    val domModel = DomManager.getDomManager(context.project).getFileElement(context.file, MavenDomProjectModel::class.java)
                   ?: return withVersion(coords, "")
    val groupId = MavenDependencyCompletionUtil.removeDummy(coords?.groupId?.stringValue)
    val artifactId = MavenDependencyCompletionUtil.removeDummy(coords?.artifactId?.stringValue)
    if (artifactId.isNotEmpty() && groupId.isNotEmpty() && coords != null) {
      if (MavenDependencyCompletionUtil.isPlugin(coords)) {
        val managedPlugin = MavenDependencyCompletionUtil.findManagedPlugin(domModel.rootElement, context.project, groupId, artifactId)
        return withVersion(coords, managedPlugin?.version?.stringValue ?: "")
      }
      if (!MavenDependencyCompletionUtil.isInsideManagedDependency(coords)) {
        val managed = MavenDependencyCompletionUtil.findManagedDependency(domModel.rootElement, context.project, groupId, artifactId)
        return withVersion(coords, managed?.version?.stringValue ?: "")
      }
    }
    return MavenId(groupId, artifactId, "")
  }

  @JvmStatic
  fun withVersion(coords: MavenDomShortArtifactCoordinates?, version: String): MavenId {
    if (coords == null) {
      return MavenId("", "", "")
    }
    return MavenId(MavenDependencyCompletionUtil.removeDummy(coords.groupId.stringValue),
                   MavenDependencyCompletionUtil.removeDummy(coords.artifactId.stringValue), version)
  }

}