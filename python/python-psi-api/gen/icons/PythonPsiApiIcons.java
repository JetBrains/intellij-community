// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


/**
 * @deprecated moved to {@link PythonParserIcons}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(forRemoval = true)
public final class PythonPsiApiIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PythonPsiApiIcons.class.getClassLoader(), cacheKey, flags);
  }

  /**
   * @deprecated moved to {@link PythonParserIcons}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated(forRemoval = true)
  /** 16x16 */ public static final @NotNull Icon PythonFile = PythonParserIcons.PythonFile;
}
