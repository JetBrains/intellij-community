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