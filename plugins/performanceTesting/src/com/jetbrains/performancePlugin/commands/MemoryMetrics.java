package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public final class MemoryMetrics {
  private final long usedMb;
  private final long maxMb;
  private final long metaspaceMb;

  MemoryMetrics(long usedMb, long maxMb, long metaspaceMb) {
    this.usedMb = usedMb;
    this.maxMb = maxMb;
    this.metaspaceMb = metaspaceMb;
  }

  @JsonProperty("usedMb")
  public long getUsedMb() {
    return usedMb;
  }

  @JsonProperty("maxMb")
  public long getMaxMb() {
    return maxMb;
  }

  @JsonProperty("metaspaceMb")
  public long getMetaspaceMb() {
    return metaspaceMb;
  }
}