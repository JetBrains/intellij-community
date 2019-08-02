package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;

import javax.swing.*;

public class TextMateSettingsUI implements ConfigurableUi<TextMateSettings>, Disposable {
  private final TextMateBundlesListPanel myBundlesListPanel;
  private final JPanel myBundlesList;

  public TextMateSettingsUI() {
    myBundlesListPanel = new TextMateBundlesListPanel();
    myBundlesList = myBundlesListPanel.createMainComponent();
    Disposer.register(this, myBundlesListPanel);
  }

  @Override
  public void apply(@NotNull TextMateSettings settings) {
    TextMateSettings.TextMateSettingsState state = settings.getState();
    if (state == null) {
      state = new TextMateSettings.TextMateSettingsState();
    }
    settings.loadState(state);
    if (myBundlesListPanel.isModified(state.getBundles())) {
      state.setBundles(myBundlesListPanel.getState());
      ProgressManager.getInstance().run(new Task.Backgroundable(null, "Loading TextMate Bundles", false,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          TextMateService.getInstance().reloadEnabledBundles();
        }
      });
    }
  }

  @Override
  public void reset(@NotNull TextMateSettings settings) {
    myBundlesListPanel.setState(settings.getBundles());
  }

  @Override
  public boolean isModified(@NotNull TextMateSettings settings) {
    final TextMateSettings.TextMateSettingsState state = settings.getState();
    if (state == null) {
      return !myBundlesListPanel.getState().isEmpty();
    }

    return myBundlesListPanel.isModified(state.getBundles());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myBundlesList;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBundlesList;
  }

  @Override
  public void dispose() {
    
  }
}
