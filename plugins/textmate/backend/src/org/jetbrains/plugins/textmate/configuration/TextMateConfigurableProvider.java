package org.jetbrains.plugins.textmate.configuration;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.SimpleConfigurable;
import org.jetbrains.annotations.NotNull;

final class TextMateConfigurableProvider extends ConfigurableProvider {
  @Override
  public @NotNull Configurable createConfigurable() {
    return SimpleConfigurable
      .create("reference.settingsdialog.textmate.bundles", IdeBundle.message("configurable.TextMateConfigurableProvider.display.name"),
              TextMateConfigurableUi.class, TextMateConfigurableData.Companion::getInstance);
  }

  @Override
  public boolean canCreateConfigurable() {
    return TextMateConfigurableData.Companion.getInstance() != null;
  }
}