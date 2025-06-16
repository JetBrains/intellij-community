// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface CommandProvider {
  ExtensionPointName<CommandProvider> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.commandProvider");

  @NotNull Map<@NonNls String, CreateCommand> getCommands();

  static @NotNull List<@NonNls String> getAllCommandNames() {
    return EP_NAME.getExtensionList().stream()
      .flatMap(e -> e.getCommands().keySet().stream()).toList();
  }

  static @Nullable CreateCommand findCommandCreator(@NonNls @NotNull String commandName) {
    return EP_NAME.getExtensionList().stream()
      .map(provider -> provider.getCommands().get(commandName))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }
}
