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
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_1_0
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenModelVersionConverter : MavenConstantListConverter() {
  override fun getValues(context: ConvertContext): Collection<String> {
    return if (isAtLeastMaven4(context.getFile().getVirtualFile(), context.getProject())) {
      VALUES_MAVEN_4
    }
    else {
      VALUES_MAVEN_3
    }
  }

  override fun fromString(s: @NonNls String?, context: ConvertContext): String? {
    if (s != null) return super.fromString(s, context)
    val rootTag = context.file.rootTag
    if (MavenUtil.isMaven410(
        rootTag?.getAttribute("xmlns")?.value,
        rootTag?.getAttribute("xsi:schemaLocation")?.value)) return MODEL_VERSION_4_1_0
    return null
  }

  override fun getErrorMessage(s: String?, context: ConvertContext): String? {
    return MavenDomBundle.message("inspection.message.unsupported.model.version.only.version.supported", getValues(context))
  }

  companion object {
    private val VALUES_MAVEN_3 = listOf(MavenConstants.MODEL_VERSION_4_0_0)
    private val VALUES_MAVEN_4 = listOf(MavenConstants.MODEL_VERSION_4_0_0,
                                        MavenConstants.MODEL_VERSION_4_1_0)
  }
}
