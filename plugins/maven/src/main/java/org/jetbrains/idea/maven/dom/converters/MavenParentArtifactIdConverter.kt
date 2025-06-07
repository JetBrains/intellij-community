// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.util.xml.ConvertContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil.getParentPomPropertyUsingRelativePath

class MavenParentArtifactIdConverter : MavenArtifactCoordinatesArtifactIdConverter() {
  override fun fromString(s: @NonNls String?, context: ConvertContext): String? {
    super.fromString(s, context)?.let {
      return it
    }
    if (isAtLeastMaven4(context.file.virtualFile, context.project)) {
      return getParentPomPropertyUsingRelativePath(context) { it.artifactId }
    }
    return null
  }
}