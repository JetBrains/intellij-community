package org.jetbrains.yaml;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author oleg
 */
public class YAMLLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(YAMLLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = defaultSettings.initIndentOptions();
    indentOptions.INDENT_SIZE = 2;
    indentOptions.USE_TAB_CHARACTER = false;
    return defaultSettings;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new YAMLIndentOptionsEditor();
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return YAMLLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return "product: \n" + "  name: RubyMine\n" + "  version: 8\n" + "  vendor: JetBrains\n" + "  url: \"https://www.jetbrains.com/ruby\"";
  }

  private class YAMLIndentOptionsEditor extends IndentOptionsEditor {

    @Override
    protected void addComponents() {
      addTabOptions();
      // Tabs in YAML are not allowed
      myCbUseTab.setEnabled(false);

      myTabSizeField = createIndentTextField();
      myTabSizeLabel = new JLabel(ApplicationBundle.message("editbox.indent.tab.size"));
      // Do not add
      //add(myTabSizeLabel, myTabSizeField);

      myIndentField = createIndentTextField();
      myIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.indent"));
      add(myIndentLabel, myIndentField);
    }

    public void setEnabled(boolean enabled) {
      // Do nothing
    }
  }

}
