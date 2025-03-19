// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardData

interface MavenArchetypeNewProjectWizardData : MavenNewProjectWizardData {

  val catalogItemProperty: GraphProperty<MavenCatalog>

  var catalogItem: MavenCatalog

  val archetypeItemProperty: GraphProperty<MavenArchetypeItem>

  var archetypeItem: MavenArchetypeItem

  val archetypeVersionProperty: GraphProperty<String>

  var archetypeVersion: String

  val archetypeDescriptorProperty: GraphProperty<Map<String, String>>

  var archetypeDescriptor: Map<String, String>

  companion object {

    val KEY = Key.create<MavenArchetypeNewProjectWizardData>(MavenArchetypeNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.archetypeMavenData: MavenArchetypeNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}