// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import org.jetbrains.idea.maven.project.MavenProject

interface MavenNewProjectWizardData : MavenizedNewProjectWizardData<MavenProject> {

  val jdkIntentProperty: GraphProperty<ProjectWizardJdkIntent>

  var jdkIntent: ProjectWizardJdkIntent

}