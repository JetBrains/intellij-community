// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import javax.swing.Icon

private const val TEST_TARGET_ID = "py-test-target"

/** A target as `PyTargetAwareAdditionalData.load` makes it: with a Python runtime. */
internal fun pythonSdkTarget(root: String): TestTargetConfiguration = TestTargetConfiguration().apply {
  displayName = "Test target"
  projectRootOnTarget = root
  addLanguageRuntime(PythonLanguageRuntimeConfiguration().apply { pythonInterpreterPath = "/usr/bin/python3" })
}

internal class TestTargetConfiguration : TargetEnvironmentConfiguration(TEST_TARGET_ID),
                                         PersistentStateComponent<TestTargetConfiguration.State> {
  class State : BaseState() {
    var root by string()
  }

  override var projectRootOnTarget: String = ""

  override fun getState(): State = State().also { it.root = projectRootOnTarget }

  override fun loadState(state: State) {
    projectRootOnTarget = state.root.orEmpty()
  }
}

/** A target type that only stores its configuration. Register it in a test to load the data of a target SDK. */
internal class TestTargetType : TargetEnvironmentType<TestTargetConfiguration>(TEST_TARGET_ID) {
  override val displayName: String = "Test"
  override val icon: Icon = EmptyIcon.ICON_16
  override fun createSerializer(config: TestTargetConfiguration): PersistentStateComponent<*> = config
  override fun createDefaultConfig(): TestTargetConfiguration = TestTargetConfiguration()
  override fun duplicateConfig(config: TestTargetConfiguration): TestTargetConfiguration = duplicateTargetConfiguration(this, config)

  override fun createEnvironmentRequest(project: Project?, config: TestTargetConfiguration): TargetEnvironmentRequest =
    throw UnsupportedOperationException("These tests prepare no environment")

  override fun createConfigurable(
    project: Project,
    config: TestTargetConfiguration,
    defaultLanguage: LanguageRuntimeType<*>?,
    parentConfigurable: Configurable?,
  ): Configurable = throw UnsupportedOperationException("These tests configure no target")
}
