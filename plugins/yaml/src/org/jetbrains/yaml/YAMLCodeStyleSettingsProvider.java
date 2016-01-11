package org.jetbrains.yaml;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class YAMLCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  @Override
  public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, YAMLLanguage.INSTANCE.getDisplayName()) {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        final CodeStyleSettings currentSettings = getCurrentSettings();
        final CodeStyleSettings settings1 = settings;
        return new TabbedLanguageCodeStylePanel(YAMLLanguage.INSTANCE, currentSettings, settings1) {
          @Override
            protected void initTabs(final CodeStyleSettings settings) {
              addIndentOptionsTab(settings);
            }
        };
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.yaml";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return YAMLLanguage.INSTANCE.getDisplayName();
  }
}
