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
  /** 16x16 */ public static final @NotNull Icon Config = load("icons/config.svg", -4125939485057738588L, 0);
  /** 16x16 */ public static final @NotNull Icon Lombok = load("icons/lombok.svg", -2187418174251863425L, 0);

  public static final class Nodes {
    /** 16x16 */ public static final @NotNull Icon LombokClass = load("icons/nodes/lombokClass.svg", -6983067847942513466L, 0);
    /** 16x16 */ public static final @NotNull Icon LombokField = load("icons/nodes/lombokField.svg", 3162444125783700060L, 0);
    /** 16x16 */ public static final @NotNull Icon LombokMethod = load("icons/nodes/lombokMethod.svg", -735727517226001206L, 1);
  }
}
