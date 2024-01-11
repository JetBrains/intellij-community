// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package icons;

import com.intellij.ui.IconManager;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import com.jetbrains.python.sdk.icons.PythonSdkIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(forRemoval = true)
public final class PythonIcons {
  private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {
    return IconManager.getInstance().loadRasterizedIcon(path, PythonIcons.class.getClassLoader(), cacheKey, flags);
  }

  /**
   * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated(forRemoval = true)
  public static final class Python {
    /**
     * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated(forRemoval = true)
    public static final class Debug {
      /**
       * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
       */
      @ApiStatus.ScheduledForRemoval
      @Deprecated(forRemoval = true) public static final @NotNull Icon SpecialVar =
        com.jetbrains.python.icons.PythonIcons.Python.Debug.SpecialVar;
    }

    /**
     * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated(forRemoval = true) public static final @NotNull Icon Python = PythonSdkIcons.Python;

    /**
     * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated(forRemoval = true) public static final @NotNull Icon PythonTests =
      com.jetbrains.python.icons.PythonIcons.Python.PythonTests;

    /**
     * @deprecated moved to {@link  com.jetbrains.python.icons.PythonIcons}
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated(forRemoval = true) public static final @NotNull Icon Virtualenv = com.jetbrains.python.icons.PythonIcons.Python.Virtualenv;
  }
}
