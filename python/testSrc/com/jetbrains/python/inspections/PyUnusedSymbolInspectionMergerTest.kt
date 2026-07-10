// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.idea.TestFor
import com.intellij.openapi.util.JDOMUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedParameterInspection

/**
 * Verifies that profile settings from the former combined `PyUnusedLocalInspection` migrate to the inspections that PY-9687 split
 * it into, so users who had disabled or reconfigured it are not silently reset to defaults.
 */
@TestFor(issues = ["PY-9687"])
class PyUnusedSymbolInspectionMergerTest : PyTestCase() {
  private lateinit var profile: InspectionProfileImpl

  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
    profile = InspectionProfileImpl("Test", InspectionToolRegistrar.getInstance(), InspectionProfileImpl("base"))
  }

  override fun tearDown() {
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = false
    }
    finally {
      super.tearDown()
    }
  }

  // A user who disabled the former combined inspection should not get the split-out inspections back, enabled, at their defaults.
  fun testDisabledStateMigratesToSplitInspections() {
    profile.readExternal(JDOMUtil.load("""
      <profile version="1.0">
        <option name="myName" value="Test" />
        <inspection_tool class="PyUnusedLocalInspection" enabled="false" level="WEAK WARNING" enabled_by_default="false" />
      </profile>"""))
    assertFalse(profile.getToolsOrNull("PyUnusedLocalVariableInspection", null)!!.isEnabled)
    assertFalse(profile.getToolsOrNull("PyUnusedParameterInspection", null)!!.isEnabled)
    assertFalse(profile.getToolsOrNull("PyUnusedFunctionInspection", null)!!.isEnabled)
  }

  // The ignoreLambdaParameters option used to live on PyUnusedLocalInspection; it must carry over to the parameter inspection.
  fun testIgnoreLambdaParametersOptionMigrates() {
    profile.readExternal(JDOMUtil.load("""
      <profile version="1.0">
        <option name="myName" value="Test" />
        <inspection_tool class="PyUnusedLocalInspection" enabled="true" level="WEAK WARNING" enabled_by_default="true">
          <option name="ignoreLambdaParameters" value="false" />
        </inspection_tool>
      </profile>"""))
    val tool = profile.getToolsOrNull("PyUnusedParameterInspection", null)!!.tool.tool as PyUnusedParameterInspection
    assertFalse(tool.ignoreLambdaParameters)
  }
}
