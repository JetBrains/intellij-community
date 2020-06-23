/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Rustam Vishnyakov
 */
public class HtmlLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return HTMLLanguage.INSTANCE;
  }

  @Override
  public String getConfigurableDisplayName() {
    return getDisplayName();
  }

  public static String getDisplayName() {
    return "HTML";
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new HtmlCodeStyleSettings(settings);
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return CodeStyleAbstractPanel.readFromFile(this.getClass(), "preview.html.indent.template");
  }

  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings,
                                                           @NotNull CodeStyleSettings modelSettings) {
    return new HtmlLanguageCodeStyleConfigurable(baseSettings, modelSettings, getDisplayName());
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    XmlLanguageCodeStyleSettingsProvider.customizeXml(consumer, settingsType);
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new HtmlIndentOptionsEditor();
  }

  private static class HtmlLanguageCodeStyleConfigurable extends CodeStyleAbstractConfigurable implements Configurable.WithEpDependencies {
    private static final Collection<BaseExtensionPointName<?>> DEPENDENCIES =
      Collections.singletonList(HtmlCodeStylePanelExtension.EP_NAME);

    private HtmlLanguageCodeStyleConfigurable(@NotNull CodeStyleSettings baseSettings,
                                              @NotNull CodeStyleSettings modelSettings,
                                              @NotNull String displayName) {
      super(baseSettings, modelSettings, displayName);
    }

    @Override
    protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
      return new HtmlCodeStyleMainPanel(getCurrentSettings(), settings);
    }

    @Override
    public String getHelpTopic() {
      return "reference.settingsdialog.IDE.globalcodestyle.html";
    }

    @Override
    public @NotNull Collection<BaseExtensionPointName<?>> getDependencies() {
      return DEPENDENCIES;
    }
  }
}
