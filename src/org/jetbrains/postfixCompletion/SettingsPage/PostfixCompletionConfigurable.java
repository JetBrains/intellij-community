package org.jetbrains.postfixCompletion.SettingsPage;

import com.intellij.openapi.options.*;
import com.intellij.openapi.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;

public final class PostfixCompletionConfigurable
  extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  @Nullable private PostfixCompletionSettingsPanel myPanel;

  @NotNull @Override public String getId() {
    return PostfixCompletionConfigurable.class.getName();
  }

  @Nls @Override public String getDisplayName() {
    return "Postfix completion";
  }

  @Nullable @Override public JComponent createComponent() {
    return myPanel = new PostfixCompletionSettingsPanel();
  }

  @Override public void apply() throws ConfigurationException {
    assert myPanel != null;
    myPanel.apply();
  }

  @Override public void reset() {
    assert myPanel != null;
    myPanel.reset();
  }

  @Override public void disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
    }

    myPanel = null;
  }

  @Nullable @Override public String getHelpTopic() {
    return null;
  }

  @Nullable @Override public Runnable enableSearch(String s) {
    return null;
  }
}