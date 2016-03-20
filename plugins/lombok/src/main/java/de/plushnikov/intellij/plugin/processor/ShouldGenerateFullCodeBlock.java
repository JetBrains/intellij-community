package de.plushnikov.intellij.plugin.processor;

/**
 * Global state for activation and deactivation of full code block generation
 * Disabled per default
 * TODO use or drop
 */
public final class ShouldGenerateFullCodeBlock {
  private static ShouldGenerateFullCodeBlock ourInstance = new ShouldGenerateFullCodeBlock();

  public static ShouldGenerateFullCodeBlock getInstance() {
    return ourInstance;
  }

  private ShouldGenerateFullCodeBlock() {
  }

  private boolean stateActive;

  public boolean isStateActive() {
    return true;
  }

  public void activate() {
    stateActive = true;
  }

  public void deactivate() {
    stateActive = false;
  }
}
