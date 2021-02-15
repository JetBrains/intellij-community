// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.accessor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class MarkdownAccessor {
  private static final SafeOpenerAccessor ourSafeOpenerAccessor = new MarkdownAccessor.SafeOpenerAccessor() {
    @Override
    public void openLink(@NotNull String link) {
      SafeLinkOpener.openLink(link);
    }

    @Override
    public boolean isSafeExtension(@Nullable String path) {
      return SafeLinkOpener.isSafeExtension(path);
    }
  };

  public static SafeOpenerAccessor getSafeOpenerAccessor() {
    return ourSafeOpenerAccessor;
  }

  public interface SafeOpenerAccessor {
    void openLink(@NotNull String link);

    boolean isSafeExtension(@Nullable String path);
  }
}
