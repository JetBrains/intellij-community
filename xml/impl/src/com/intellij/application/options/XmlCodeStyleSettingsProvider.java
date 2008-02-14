package com.intellij.application.options;

import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class XmlCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, ApplicationBundle.message("title.xml")){
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new CodeStyleXmlPanel(settings);
      }
      public Icon getIcon() {
        return StdFileTypes.XML.getIcon();
      }

      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.xml";
      }
    };
  }
}
