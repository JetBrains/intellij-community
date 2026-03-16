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

import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_0_0
import org.jetbrains.idea.maven.project.MavenProject

class MavenPackagingConverter : MavenProjectConstantListConverter(false) {
  override fun getValues(context: ConvertContext, project: MavenProject): Collection<String> {
    val result = mutableSetOf<String>()
    result.addAll(DEFAULT_PACKAGES)
    for (each: MavenImporter in MavenImporter.getSuitableImporters(project)) {
      each.getSupportedPackagings(result)
    }
    val file = context.file
    val model =
      DomManager.getDomManager(file.getProject()).getFileElement(file, MavenDomProjectModel::class.java)
    val rootElement = model?.rootElement
    val modelVersion = rootElement?.modelVersion?.stringValue?.trim()
    if (modelVersion != MODEL_VERSION_4_0_0) {
      result.addAll(MAVEN_4_SPECIFIC)
    }
    return result
  }

  companion object {
    val DEFAULT_PACKAGES: Set<String> = setOf(MavenConstants.TYPE_POM, MavenConstants.TYPE_JAR, "ejb", "ejb-client", "war", "ear", "bundle", "maven-plugin")
    val MAVEN_4_SPECIFIC: Set<String> = setOf("bom")
  }
}