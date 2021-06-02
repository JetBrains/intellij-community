// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.LabelAndComponent
import com.intellij.ide.projectWizard.generators.JavaBuildSystemType
import com.intellij.ide.projectWizard.generators.JavaSettings
import com.intellij.ui.components.JBTextField
import javax.swing.JLabel

class MavenJavaBuildSystemType: JavaBuildSystemType("Maven") {
  override fun setupProject(settings: JavaSettings) {
    //TODO("Not yet implemented")
  }

  private val groupId: JBTextField = JBTextField()
  private val artifactId: JBTextField = JBTextField()
  private val version: JBTextField = JBTextField()


  override val advancedSettings: List<LabelAndComponent> = listOf(
    LabelAndComponent(JLabel("GroupId"), groupId),
    LabelAndComponent(JLabel("ArtifactId"), artifactId),
    LabelAndComponent(JLabel("Version"), version),
  )
}