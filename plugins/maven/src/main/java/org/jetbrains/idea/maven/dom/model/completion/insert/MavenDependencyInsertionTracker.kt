// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.completion.insert

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.statistics.MavenDependencyInsertionCollector

internal fun logMavenDependencyInsertion(context: InsertionContext, item: LookupElement, completionItem: MavenRepositoryArtifactInfo) {
  val groupId = completionItem.groupId
  val artifactId = completionItem.artifactId
  val version = completionItem.version ?: ""

  val completionPrefix = item.getUserData(MavenCoordinateCompletionContributor.MAVEN_COORDINATE_COMPLETION_PREFIX_KEY) ?: ""
  val selectedLookupIndex = context.elements.indexOf(item)

  MavenDependencyInsertionCollector.logPackageAutoCompleted(
    groupId, artifactId, version,
    MavenDependencyInsertionCollector.Companion.BuildSystem.MAVEN,
    MavenDependencyInsertionCollector.Companion.DependencyDeclarationNotation.MAVEN,
    completionPrefix.length,
    selectedLookupIndex
  )
}