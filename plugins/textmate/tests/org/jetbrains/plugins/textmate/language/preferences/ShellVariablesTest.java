package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.jetbrains.plugins.textmate.TestUtil.HTML;
import static org.jetbrains.plugins.textmate.TestUtil.getBundle;
import static org.junit.Assert.*;

public class ShellVariablesTest {
  @Test
  public void retrieveVariableValueBySelector() throws IOException {
    final ShellVariablesRegistry variablesRegistry = loadVariables(HTML);
    assertEquals("<!-- ", variablesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, "text.html.basic").value);
  }

  @Test
  public void retrieveVariableValueByUnmatchedSelector() throws IOException {
    final ShellVariablesRegistry preferencesRegistry = loadVariables(HTML);
    assertNull(preferencesRegistry.getVariableValue(Constants.COMMENT_START_VARIABLE, "text.unknown.basic"));
  }
  
  @NotNull
  private static ShellVariablesRegistry loadVariables(@NotNull String bundleName) throws IOException {
    final Bundle bundle = getBundle(bundleName);
    assertNotNull(bundle);
    final ShellVariablesRegistry variablesRegistry = new ShellVariablesRegistry();
    for (File file : bundle.getPreferenceFiles()) {
      for (Pair<String, Plist> settingsPair : bundle.loadPreferenceFile(file)) {
        if (settingsPair != null) {
          variablesRegistry.fillVariablesFromPlist(settingsPair.first, settingsPair.second);
        }
      }
    }
    return variablesRegistry;
  }
}
