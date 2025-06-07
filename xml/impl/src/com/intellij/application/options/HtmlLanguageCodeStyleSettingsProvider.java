// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class HtmlLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull Language getLanguage() {
    return HTMLLanguage.INSTANCE;
  }

  @Override
  public String getConfigurableDisplayName() {
    return getDisplayName();
  }

  public static @ConfigurableName String getDisplayName() {
    return XmlCoreBundle.message("options.html.display.name");
  }

  @Override
  public @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
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

  private static final class HtmlLanguageCodeStyleConfigurable extends CodeStyleAbstractConfigurable implements Configurable.WithEpDependencies {
    private static final Collection<BaseExtensionPointName<?>> DEPENDENCIES =
      Collections.singletonList(HtmlCodeStylePanelExtension.EP_NAME);

    private HtmlLanguageCodeStyleConfigurable(@NotNull CodeStyleSettings baseSettings,
                                              @NotNull CodeStyleSettings modelSettings,
                                              @NotNull @ConfigurableName String displayName) {
      super(baseSettings, modelSettings, displayName);
    }

    @Override
    protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
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
