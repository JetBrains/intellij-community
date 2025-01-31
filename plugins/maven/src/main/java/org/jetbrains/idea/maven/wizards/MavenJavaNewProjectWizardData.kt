// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.Key

interface MavenJavaNewProjectWizardData : MavenNewProjectWizardData {

  val addSampleCodeProperty: GraphProperty<Boolean>

  var addSampleCode: Boolean

  @Deprecated("Use addSampleCodeProperty instead")
  val generateOnboardingTipsProperty: ObservableMutableProperty<Boolean>
    get() = addSampleCodeProperty

  @Deprecated("Use addSampleCode instead")
  val generateOnboardingTips: Boolean
    get() = addSampleCode

  companion object {

    val KEY = Key.create<MavenJavaNewProjectWizardData>(MavenJavaNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.javaMavenData: MavenJavaNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}