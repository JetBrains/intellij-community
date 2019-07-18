package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.TextMateService;

import javax.swing.*;

public class TextMateSettingsUI implements ConfigurableUi<TextMateSettings>, Disposable {
  private TextMateBundlesListPanel myBundlesListPanel;
  private TextMateThemeMappingPanel myThemeMappingPanel;

  private JPanel myPanel;
  @SuppressWarnings("unused") private JPanel myBundlesList;
  @SuppressWarnings("unused") private JPanel myThemeMappingComponent;


  @Override
  public void apply(@NotNull TextMateSettings settings) {
    TextMateSettings.TextMateSettingsState state = settings.getState();
    if (state == null) {
      state = new TextMateSettings.TextMateSettingsState();
    }
    settings.loadState(state);
    if (myThemeMappingPanel.isModified(state.getThemesMapping())) {
      myThemeMappingPanel.apply(state);
      EditorFactory.getInstance().refreshAllEditors();
    }
    if (myBundlesListPanel.isModified(state.getBundles())) {
      final TextMateService textMateService = TextMateService.getInstance();
      state.setBundles(myBundlesListPanel.getState());
      ProgressManager.getInstance().run(new Task.Backgroundable(null, "Loading TextMate Bundles", false,
                                                                PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          textMateService.unregisterAllBundles(false);
          textMateService.reloadThemesFromDisk();
          textMateService.registerEnabledBundles(true);
        }
      });
    }
  }

  @Override
  public void reset(@NotNull TextMateSettings settings) {
    myThemeMappingPanel.reset(settings);
    myBundlesListPanel.setState(settings.getBundles());
  }

  @Override
  public boolean isModified(@NotNull TextMateSettings settings) {
    final TextMateSettings.TextMateSettingsState state = settings.getState();
    if (state == null) {
      return !myBundlesListPanel.getState().isEmpty() ||
             myThemeMappingPanel.isModified(myThemeMappingPanel.getDefaultThemesMapping(settings));
    }

    return myBundlesListPanel.isModified(state.getBundles())
           || myThemeMappingPanel.isModified(state.getThemesMapping());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }


  @Override
  public void dispose() {
    myBundlesList = null;
    myThemeMappingComponent = null;
  }

  private static String[] retrieveAllSchemeNames() {
    return ContainerUtil.map2Array(EditorColorsManager.getInstance().getAllSchemes(), String.class, s -> SchemeManager.getDisplayName(s));
  }

  private void createUIComponents() {
    myBundlesListPanel = new TextMateBundlesListPanel();
    myBundlesList = myBundlesListPanel.createMainComponent();
    myThemeMappingPanel = new TextMateThemeMappingPanel(TextMateService.getInstance().getThemeNames(), retrieveAllSchemeNames());
    myThemeMappingComponent = myThemeMappingPanel.getMainComponent();
    if (Registry.is("textmate.theme.emulation")) {
      myThemeMappingComponent.setVisible(false);
    }
    Disposer.register(this, myThemeMappingPanel);
    Disposer.register(this, myBundlesListPanel);
  }
}
