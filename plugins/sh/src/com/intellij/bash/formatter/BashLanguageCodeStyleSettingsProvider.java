package com.intellij.bash.formatter;

import com.intellij.application.options.*;
import com.intellij.bash.BashLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

public class BashLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

  @NotNull
  @Override
  public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(settings, modelSettings, "Bash") {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new BashCodeStyleMainPanel(settings, modelSettings);
      }
    };
  }

  @NotNull
  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return GENERAL_CODE_SAMPLE;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return BashLanguage.INSTANCE;
  }

  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 2;
    indentOptions.TAB_SIZE = 2;
  }

  private static final String GENERAL_CODE_SAMPLE = "#!/usr/bin/env bash\n" +
      "\n" +
      "function foo() {\n" +
      "  if [ -x $file ]; then\n" +
      "    myArray=(item1 item2 item3)\n" +
      "  elif [ $file1 -nt $file2 ]; then\n" +
      "    unset myArray\n" +
      "  else\n" +
      "    echo \"Usage: $0 file ...\"\n" +
      "  fi\n" +
      "}\n" +
      "\n" +
      "for (( i = 0; i < 5; i++ )); do\n" +
      "  read -p r\n" +
      "  print -n $r\n" +
      "  wait $!\n" +
      "done\n" +
      "\n" +
      "cat <<EOF\n" +
      "  Some text\n" +
      "EOF";
}
