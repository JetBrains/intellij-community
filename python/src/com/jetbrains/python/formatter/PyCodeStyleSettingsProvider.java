package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new PyCodeStyleSettings(settings);
  }

  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, "Python") {
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new PyCodeStyleMainPanel(getCurrentSettings(), settings);
      }

      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.python";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return "Python";
  }

  @Override
  public DisplayPriority getPriority() {
    return PlatformUtils.isPyCharm() ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }
}
