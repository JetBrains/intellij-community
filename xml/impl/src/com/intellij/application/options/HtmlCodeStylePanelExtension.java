// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ApiStatus.Experimental
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


  static List<HtmlPanelCustomizer> getCustomizers() {
    HtmlCodeStylePanelExtension[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return ContainerUtil.emptyList();

    return Arrays.stream(extensions).map(el -> el.getCustomizer()).collect(Collectors.toList());
  }
}
