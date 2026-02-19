// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class SpellcheckerIcons {
  private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, expUIPath, SpellcheckerIcons.class.getClassLoader(), cacheKey, flags);
  }
  /** 16x16 */ public static final @NotNull Icon Dictionary = load("icons/newui/dictionary.svg", "icons/dictionary.svg", -2094657776, 2);
  /** 16x16 */ public static final @NotNull Icon Spellcheck = load("icons/newui/addToDictionary.svg", "icons/spellcheck.svg", 1919442669, 2);
}
