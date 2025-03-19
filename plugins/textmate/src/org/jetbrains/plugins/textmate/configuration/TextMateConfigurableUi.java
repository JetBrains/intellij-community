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
import org.jetbrains.plugins.textmate.TextMateBundle;
import org.jetbrains.plugins.textmate.TextMateService;

import javax.swing.*;
import java.util.Set;

public class TextMateConfigurableUi implements ConfigurableUi<TextMateConfigurableData>, Disposable {
  private final TextMateBundlesListPanel myBundlesListPanel;
  private final JPanel myBundlesList;

  public TextMateConfigurableUi() {
    myBundlesListPanel = new TextMateBundlesListPanel();
    myBundlesList = myBundlesListPanel.createMainComponent();
    Disposer.register(this, myBundlesListPanel);
  }

  @Override
  public void apply(@NotNull TextMateConfigurableData settings) {
    Set<TextMateConfigurableBundle> state = settings.getConfigurableBundles();
    if (myBundlesListPanel.isModified(state)) {
      settings.applySettings(myBundlesListPanel.getState());
      ProgressManager.getInstance().run(new Task.Backgroundable(null, TextMateBundle.message("textmate.loading.bundles.title"), false,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          TextMateService.getInstance().reloadEnabledBundles();
        }
      });
    }
  }

  @Override
  public void reset(@NotNull TextMateConfigurableData settings) {
    myBundlesListPanel.setState(settings.getConfigurableBundles());
  }

  @Override
  public boolean isModified(@NotNull TextMateConfigurableData settings) {
    return myBundlesListPanel.isModified(settings.getConfigurableBundles());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myBundlesList;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myBundlesList;
  }

  @Override
  public void dispose() {
    
  }
}
