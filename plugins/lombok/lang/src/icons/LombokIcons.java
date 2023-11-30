package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class LombokIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, LombokIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Config = load("icons/config.svg", -951082075, 0);
  /** 16x16 */ public static final @NotNull Icon Lombok = load("icons/lombok.svg", 1713619590, 0);

  public static final class Nodes {
    /** 16x16 */ public static final @NotNull Icon LombokClass = load("icons/nodes/lombokClass.svg", 192706796, 0);
    /** 16x16 */ public static final @NotNull Icon LombokField = load("icons/nodes/lombokField.svg", -2139677246, 0);
    /** 16x16 */ public static final @NotNull Icon LombokMethod = load("icons/nodes/lombokMethod.svg", 646423920, 1);
  }
}
