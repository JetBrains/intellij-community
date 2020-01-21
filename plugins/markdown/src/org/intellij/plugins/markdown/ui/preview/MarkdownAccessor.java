// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownAccessor {

  private static ImageRefreshFixAccessor ourImageRefreshFixAccessor;
  private static SafeOpenerAccessor ourSafeOpenerAccessor;

  public static void setImageRefreshFixAccessor(@NotNull ImageRefreshFixAccessor accessor) {
    ourImageRefreshFixAccessor = accessor;
  }

  public static void setSafeOpenerAccessor(@NotNull SafeOpenerAccessor accessor) {
    ourSafeOpenerAccessor = accessor;
  }

  @NotNull
  public static ImageRefreshFixAccessor getImageRefreshFixAccessor() {
    if (ourImageRefreshFixAccessor == null) {
      try {
        Class.forName(MarkdownAccessor.class.getPackage().getName() + ".javafx.ImageRefreshFix", true, MarkdownAccessor.class.getClassLoader());
      } catch (ClassNotFoundException e) {
        ourImageRefreshFixAccessor = new ImageRefreshFixAccessor() {
          @Override
          public String setStamps(@NotNull String html) {
            return html;
          }
        };
      }
    }
    return ourImageRefreshFixAccessor;
  }

  @NotNull
  public static SafeOpenerAccessor getSafeOpenerAccessor() {
    if (ourSafeOpenerAccessor == null) {
      try {
        Class.forName(MarkdownAccessor.class.getPackage().getName() + ".javafx.SafeOpener", true, MarkdownAccessor.class.getClassLoader());
      }
      catch (ClassNotFoundException e) {
        ourSafeOpenerAccessor = new SafeOpenerAccessor() {
          @Override
          public void openLink(@NotNull String link) {
          }
          @Override
          public boolean isSafeExtension(@Nullable String path) {
            return true;
          }
        };
      }
    }
    return ourSafeOpenerAccessor;
  }

  public interface ImageRefreshFixAccessor {
    String setStamps(@NotNull String html);
  }

  public interface SafeOpenerAccessor {
    void openLink(@NotNull String link);
    boolean isSafeExtension(@Nullable String path);
  }
}
