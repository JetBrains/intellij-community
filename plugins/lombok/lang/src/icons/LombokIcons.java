package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class LombokIcons {
  private static @NotNull Icon load(@NotNull String path, @NotNull String expUIPath, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, LombokIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Config = load("icons/config.svg", "icons/expui/config.svg", -951082075, 0);
  /** 16x16 */ public static final @NotNull Icon Lombok = load("icons/lombok.svg", "icons/expui/lombok.svg", 1713619590, 0);

  public static final class Nodes {
    /** 16x16 */ public static final @NotNull Icon LombokClass = load("icons/nodes/lombokClass.svg", "icons/expui/nodes/lombokClass.svg", 192706796, 0);
    /** 16x16 */ public static final @NotNull Icon LombokField = load("icons/nodes/lombokField.svg", "icons/expui/nodes/lombokField.svg", -2139677246, 0);
    /** 16x16 */ public static final @NotNull Icon LombokMethod = load("icons/nodes/lombokMethod.svg", "icons/expui/nodes/lombokMethod.svg", 646423920, 1);
  }
}
