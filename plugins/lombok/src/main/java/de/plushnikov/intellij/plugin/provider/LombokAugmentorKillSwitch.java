package de.plushnikov.intellij.plugin.provider;

public final class LombokAugmentorKillSwitch {
  private static boolean lombokAugmentorActive = true;

  public static boolean isLombokAugmentorActive() {
    return lombokAugmentorActive;
  }

  public static void activateLombokAugmentor() {
    lombokAugmentorActive = true;
  }

  public static void deactivateLombokAugmentor() {
    lombokAugmentorActive = false;
  }
}
