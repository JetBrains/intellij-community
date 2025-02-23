package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShellVariablesTest {
  @Test
  fun retrieveVariableValueBySelector() {
    val variablesRegistry: ShellVariablesRegistry = loadVariables(TestUtil.HTML)
    assertEquals("<!-- ",
                        variablesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE,
                                                           TestUtil.scopeFromString("text.html.basic"))!!.value)
  }

  @Test
  fun retrieveVariableValueByUnmatchedSelector() {
    val preferencesRegistry: ShellVariablesRegistry = loadVariables(TestUtil.HTML)
    assertNull(
      preferencesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, TestUtil.scopeFromString("text.unknown.basic")))
  }

  companion object {
    private fun loadVariables(bundleName: String): ShellVariablesRegistry {
      val preferences = TestUtil.readBundle(bundleName).readPreferences().iterator()
      assertNotNull(preferences)
      val variablesRegistry = ShellVariablesRegistryImpl(TextMateSelectorWeigherImpl())
      while (preferences.hasNext()) {
        for (variable in preferences.next().variables) {
          variablesRegistry.addVariable(variable)
        }
      }
      return variablesRegistry
    }
  }
}
