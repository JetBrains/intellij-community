// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.util.xml.ConvertContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.model.MavenConstants

import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenModelVersionConverter : MavenConstantListConverter(false) {
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
    return if (isAtLeastMaven4(context.getFile().getVirtualFile(), context.project)) {
      val xmlns = context.file.rootTag?.getAttribute("xmlns")?.value
      MavenUtil.inferModelVersionFromNamespace(xmlns)
    }
    else {
      null
    }
  }

  override fun getErrorMessage(s: String?, context: ConvertContext): String {
    val project = context.project
    val version = MavenDistributionsCache.getInstance(project).getMavenVersion(context.file.virtualFile)
    return MavenDomBundle.message("inspection.message.unsupported.model.version.only.version.supported", getValues(context), version)
  }

  companion object {
    private val VALUES_MAVEN_3 = listOf(MavenConstants.MODEL_VERSION_4_0_0)
    private val VALUES_MAVEN_4 = listOf(MavenConstants.MODEL_VERSION_4_0_0,
                                        MavenConstants.MODEL_VERSION_4_1_0)
  }
}
