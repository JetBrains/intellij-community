/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters

import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenId

object MavenArtifactCoordinatesHelper {
  @JvmStatic
  fun getId(context: ConvertContext): MavenId {
    return getMavenId(getCoordinates(context), context)
  }

  @JvmStatic
  fun getCoordinates(context: ConvertContext): MavenDomShortArtifactCoordinates? {
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
    if (artifactId.isNotEmpty() && groupId.isNotEmpty() && (coords!=null && !MavenDependencyCompletionUtil.isInsideManagedDependency(coords))) {
      val managed = MavenDependencyCompletionUtil.findManagedDependency(domModel.rootElement, context.project, groupId,
                                                                        artifactId)
      return withVersion(coords, managed?.version?.stringValue ?: "")
    }
    else {
      return MavenId(groupId, artifactId, "")
    }
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