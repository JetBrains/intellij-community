// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.idea.reposearch.statistics.TopPackageIdValidationRule


class MavenDependencyInsertionCollector : CounterUsagesCollector() {
  private val logger = Logger.getInstance(MavenDependencyInsertionCollector::class.java)

  override fun getGroup() = GROUP

  companion object {
    private const val GROUP_ID = "gradle.maven.count"
    private const val VERSION = 1

    private val GROUP = EventLogGroup(GROUP_ID, VERSION)

    private const val PACKAGE_ID = "package_id"
    private const val PACKAGE_VERSION = "package_version"
    private const val BUILD_SYSTEM = "build_system"
    private const val DEPENDENCY_DECLARATION_NOTATION = "dependency_declaration_notation"

    private const val COMPLETION_PREFIX_LENGTH = "completion_prefix_length"
    private const val SELECTED_LOOKUP_INDEX = "selected_lookup_index"

    private const val PACKAGE_AUTOCOMPLETED = "package_autocompleted"
    private const val PACKAGE_COPYPASTED = "package_copypasted"

    enum class BuildSystem {
      @JvmField
      GRADLE,

      @JvmField
      MAVEN
    }

    enum class DependencyDeclarationNotation {
      @JvmField
      GRADLE_STRING_STYLE,

      @JvmField
      GRADLE_MAP_STYLE,

      @JvmField
      MAVEN
    }

    private val packageIdField = EventFields.StringValidatedByCustomRule(PACKAGE_ID, TopPackageIdValidationRule::class.java)
    private val packageVersionField = EventFields.StringValidatedByRegexp(PACKAGE_VERSION, regexpRef = "version")
    private val completionPrefixLengthField = EventFields.Int(COMPLETION_PREFIX_LENGTH)
    private val selectedLookupIndexField = EventFields.Int(SELECTED_LOOKUP_INDEX)
    private val buildSystemField = EventFields.Enum<BuildSystem>(BUILD_SYSTEM)
    private val dependencyDeclarationNotationField =
      EventFields.Enum<DependencyDeclarationNotation>(DEPENDENCY_DECLARATION_NOTATION)

    private val packageAutoCompleted = GROUP.registerVarargEvent(
      eventId = PACKAGE_AUTOCOMPLETED,
      packageIdField,
      packageVersionField,
      buildSystemField,
      dependencyDeclarationNotationField,
      completionPrefixLengthField,
      selectedLookupIndexField
    )

    private val packageCopyPasted = GROUP.registerVarargEvent(
      eventId = PACKAGE_COPYPASTED,
      packageIdField,
      packageVersionField,
      buildSystemField,
      dependencyDeclarationNotationField
    )

    @JvmStatic
    fun logPackageAutoCompleted(
      groupId: String,
      artifactId: String,
      version: String,
      buildSystem: BuildSystem,
      dependencyDeclarationNotation: DependencyDeclarationNotation,
      completionPrefixLength: Int,
      selectedLookupIndex: Int
    ) {
      val packageId = "$groupId:$artifactId"
      packageAutoCompleted.log(
        packageIdField.with(packageId),
        packageVersionField.with(version),
        buildSystemField.with(buildSystem),
        dependencyDeclarationNotationField.with(dependencyDeclarationNotation),
        completionPrefixLengthField.with(completionPrefixLength),
        selectedLookupIndexField.with(selectedLookupIndex),
      )
    }

    @JvmStatic
    fun logPackageCopyPasted(
      groupId: String,
      artifactId: String,
      version: String,
      buildSystem: BuildSystem,
      dependencyDeclarationNotation: DependencyDeclarationNotation
    ) {
      val packageId = "$groupId:$artifactId"
      packageCopyPasted.log(
        packageIdField.with(packageId),
        packageVersionField.with(version),
        buildSystemField.with(buildSystem),
        dependencyDeclarationNotationField.with(dependencyDeclarationNotation)
      )
    }
  }
}
