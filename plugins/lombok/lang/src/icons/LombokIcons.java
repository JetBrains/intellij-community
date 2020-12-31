package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class LombokIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, LombokIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Config = load("icons/config.png", 0L, 1);
  /** 16x16 */ public static final @NotNull Icon Delombok = load("icons/delombok.png", 0L, 1);
  /** 16x16 */ public static final @NotNull Icon Lombok = load("icons/lombok.png", 0L, 1);

  public static final class Nodes {
    /** 16x16 */ public static final @NotNull Icon LombokClass = load("icons/nodes/lombokClass.png", 0L, 1);
    /** 16x16 */ public static final @NotNull Icon LombokField = load("icons/nodes/lombokField.png", 0L, 1);
    /** 16x16 */ public static final @NotNull Icon LombokMethod = load("icons/nodes/lombokMethod.png", 0L, 1);
  }
}
