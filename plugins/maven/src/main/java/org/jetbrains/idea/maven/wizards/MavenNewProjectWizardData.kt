// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.externalSystem.service.project.wizard.MavenizedNewProjectWizardData
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import org.jetbrains.idea.maven.project.MavenProject

interface MavenNewProjectWizardData : MavenizedNewProjectWizardData<MavenProject> {

  val sdkProperty: GraphProperty<Sdk?>

  var sdk: Sdk?

  val sdkDownloadTaskProperty: ObservableMutableProperty<SdkDownloadTask?>

  var sdkDownloadTask: SdkDownloadTask?
}