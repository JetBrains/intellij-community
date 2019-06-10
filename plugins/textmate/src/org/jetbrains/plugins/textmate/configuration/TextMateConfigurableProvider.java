package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.SimpleConfigurable;
import org.jetbrains.annotations.Nullable;

public class TextMateConfigurableProvider extends ConfigurableProvider {
  @Nullable
  @Override
  public Configurable createConfigurable() {
    return SimpleConfigurable.create("reference.settingsdialog.textmate.bundles", "TextMate Bundles", TextMateSettingsUI.class,
                                     TextMateSettings::getInstance);
  }

  @Override
  public boolean canCreateConfigurable() {
    return TextMateSettings.getInstance() != null;
  }
}