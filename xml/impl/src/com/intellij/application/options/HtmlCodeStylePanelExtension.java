// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.List;

public interface HtmlCodeStylePanelExtension {
  ExtensionPointName<HtmlCodeStylePanelExtension> EP_NAME = ExtensionPointName.create("com.intellij.html.codestyle.panel");

  interface HtmlPanelCustomizer {
    void customizeSettingsPanel(@NotNull JPanel settingsPanel);

    void reset(@NotNull CodeStyleSettings rootSettings);

    boolean isModified(@NotNull CodeStyleSettings rootSettings);

    void apply(@NotNull CodeStyleSettings rootSettings);
  }

  @NotNull
  HtmlPanelCustomizer getCustomizer();

  static @Unmodifiable @NotNull List<HtmlPanelCustomizer> getCustomizers() {
    List<HtmlCodeStylePanelExtension> extensions = EP_NAME.getExtensionList();
    if (extensions.isEmpty()) {
      return ContainerUtil.emptyList();
    }
    return ContainerUtil.map(extensions, el -> el.getCustomizer());
  }
}
