// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.ui

import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import icons.MavenIcons
import javax.swing.Icon

class MavenIconProvider : ExternalSystemIconProvider {

  override val reloadIcon: Icon = MavenIcons.MavenLoadChanges

  override val projectIcon: Icon = MavenIcons.MavenProject
}