// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.idea.TestFor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyTypeCheckerInspection
import com.jetbrains.python.inspections.quickfix.PySuppressWithTypeIgnoreFix
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection

@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = ["PY-90780"])
class PySuppressWithTypeIgnoreFixTest : PyTestCase() {

  private val fixName = PyPsiBundle.message("INSP.python.suppressor.suppress.with.type.ignore")

  fun testInsertsCodedTrailingComment() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)
      """,
      """
        def foo(x: str):
            print(x.bar)  # type: ignore[unresolved-references]
      """,
    )
  }

  fun testWithPyCharmNamespace() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)
      """,
      """
        def foo(x: str):
            print(x.bar)  # type: ignore[pycharm:unresolved-references]
      """,
      pycharmNamespace = true,
    )
  }

  fun testBareWhenCodeExcluded() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)
      """,
      """
        def foo(x: str):
            print(x.bar)  # type: ignore
      """,
      includeCode = false,
    )
  }

  fun testMergesIntoExistingTypeIgnore() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)  # type: ignore[bad-return]
      """,
      """
        def foo(x: str):
            print(x.bar)  # type: ignore[bad-return, unresolved-references]
      """,
    )
  }

  @TestFor(issues = ["PY-90780", "PY-90627"])
  fun testMergesIntoExistingPyCharmIgnore() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)  # pycharm: ignore[bad-return]
      """,
      """
        def foo(x: str):
            print(x.bar)  # pycharm: ignore[bad-return, unresolved-references]
      """,
    )
  }

  /** The existing comment names a PyCharm code, so a bare comment would suppress more. The fix adds the code. */
  fun testAddsCodeToExistingIgnoreWhenCodeExcluded() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)  # type: ignore[bad-return]
      """,
      """
        def foo(x: str):
            print(x.bar)  # type: ignore[bad-return, unresolved-references]
      """,
      includeCode = false,
    )
  }

  /** The `# pycharm: ignore` directive already makes every code a PyCharm code. */
  @TestFor(issues = ["PY-90780", "PY-90627"])
  fun testNoNamespaceInPyCharmIgnore() {
    doFixTest(
      """
        def foo(x: str):
            print(x.ba<caret>r)  # pycharm: ignore[bad-return]
      """,
      """
        def foo(x: str):
            print(x.bar)  # pycharm: ignore[bad-return, unresolved-references]
      """,
      pycharmNamespace = true,
    )
  }

  @TestFor(issues = ["PY-90780", "PY-90787"])
  fun testGranularTypeCheckerCode() {
    doFixTest(
      """
        print(2 + <caret>'foo')
      """,
      """
        print(2 + 'foo')  # type: ignore[unsupported-operator]
      """,
      inspection = PyTypeCheckerInspection::class.java,
    )
  }

  private fun doFixTest(
    before: String,
    after: String,
    includeCode: Boolean = true,
    pycharmNamespace: Boolean = false,
    inspection: Class<out com.intellij.codeInspection.LocalInspectionTool> = PyUnresolvedReferencesInspection::class.java,
  ) {
    val oldInclude = AdvancedSettings.getBoolean(PySuppressWithTypeIgnoreFix.INCLUDE_CODE_SETTING)
    val oldNamespace = AdvancedSettings.getBoolean(PySuppressWithTypeIgnoreFix.PYCHARM_NAMESPACE_SETTING)
    AdvancedSettings.setBoolean(PySuppressWithTypeIgnoreFix.INCLUDE_CODE_SETTING, includeCode)
    AdvancedSettings.setBoolean(PySuppressWithTypeIgnoreFix.PYCHARM_NAMESPACE_SETTING, pycharmNamespace)
    try {
      myFixture.enableInspections(inspection)
      myFixture.configureByText(PythonFileType.INSTANCE, before.trimIndent())
      myFixture.doHighlighting()
      // The suppressor EP can offer the (identically named) action more than once for a single highlight; the
      // real Alt-Enter popup de-duplicates by text, so just take the first.
      val intention = myFixture.filterAvailableIntentions(fixName).first()
      myFixture.launchAction(intention)
      myFixture.checkResult(after.trimIndent())
    }
    finally {
      AdvancedSettings.setBoolean(PySuppressWithTypeIgnoreFix.INCLUDE_CODE_SETTING, oldInclude)
      AdvancedSettings.setBoolean(PySuppressWithTypeIgnoreFix.PYCHARM_NAMESPACE_SETTING, oldNamespace)
    }
  }
}
