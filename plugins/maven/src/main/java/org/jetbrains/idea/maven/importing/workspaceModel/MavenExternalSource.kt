// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.workspaceModel.ide.ExternalEntitySource

object MavenExternalSource {
  @JvmStatic
  val INSTANCE = ExternalEntitySource("Maven", "maven")
  val INSTANCE_PM = object : ProjectModelExternalSource {
    override fun getId() = INSTANCE.id

    override fun getDisplayName() = INSTANCE.displayName
  }
}
