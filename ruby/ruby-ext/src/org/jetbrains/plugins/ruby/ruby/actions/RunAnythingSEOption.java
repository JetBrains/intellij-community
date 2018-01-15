package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.util.registry.Registry;

class RunAnythingSEOption extends BooleanOptionDescription {
  private final String myKey;

  public RunAnythingSEOption(String option, String registryKey) {
    super(option, null);
    myKey = registryKey;
  }

  @Override
  public boolean isOptionEnabled() {
    return Registry.is(myKey);
  }

  @Override
  public void setOptionState(boolean enabled) {
    Registry.get(myKey).setValue(enabled);
  }
}
