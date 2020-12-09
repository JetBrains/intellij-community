package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class PackageSearchIcons {
  private static @NotNull Icon load(@NotNull String path, long cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PackageSearchIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Artifact = load("icons/artifact.svg", 3672511844568877953L, 2);
  /** 13x13 */ public static final @NotNull Icon ArtifactSmall = load("icons/artifactSmall.svg", -1825013553400867350L, 2);

  public static final class Operations {
    /** 16x16 */ public static final @NotNull Icon Downgrade = load("icons/operations/downgrade.svg", 283807573797228400L, 2);
    /** 16x16 */ public static final @NotNull Icon Install = load("icons/operations/install.svg", -3446408694057121609L, 2);
    /** 16x16 */ public static final @NotNull Icon Remove = load("icons/operations/remove.svg", 5951222181846376483L, 2);
    /** 16x16 */ public static final @NotNull Icon Upgrade = load("icons/operations/upgrade.svg", 6615960348119988520L, 2);
  }

  /** 16x16 */ public static final @NotNull Icon Package = load("icons/package.svg", -8036164534226080593L, 0);
  /** 16x16 */ public static final @NotNull Icon StackOverflow = load("icons/stackOverflow.svg", 8854128273388186082L, 2);
}
