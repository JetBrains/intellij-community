// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public final class ExitMetrics {
  private final MemoryMetrics memory;

  ExitMetrics(MemoryMetrics memory) {
    this.memory = memory;
  }

  @JsonProperty("memory")
  public MemoryMetrics getMemory() {
    return memory;
  }
}