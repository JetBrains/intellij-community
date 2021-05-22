// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.settings;

import com.intellij.stats.completion.sender.SenderPreloadingActivityKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link com.intellij.completion.ml.settings.CompletionMLRankingSettings} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
public final class CompletionMLRankingSettings {
  private static final CompletionMLRankingSettings instance = new CompletionMLRankingSettings();

  private final com.intellij.completion.ml.settings.CompletionMLRankingSettings actualInstance =
    com.intellij.completion.ml.settings.CompletionMLRankingSettings.getInstance();

  @NotNull
  public static CompletionMLRankingSettings getInstance() {
    return instance;
  }

  public boolean isRankingEnabled() {
    return actualInstance.isRankingEnabled();
  }

  public void setRankingEnabled(boolean value) {
    actualInstance.setRankingEnabled(value);
  }

  public boolean isLanguageEnabled(@NotNull String rankerId) {
    return actualInstance.isLanguageEnabled(rankerId);
  }

  public void setLanguageEnabled(@NotNull String rankerId, boolean isEnabled) {
    actualInstance.setLanguageEnabled(rankerId, isEnabled);
  }

  public boolean isShowDiffEnabled() {
    return actualInstance.isShowDiffEnabled();
  }

  public void setShowDiffEnabled(boolean isEnabled) {
    actualInstance.setShowDiffEnabled(isEnabled);
  }

  public boolean isCompletionLogsSendAllowed() {
    return SenderPreloadingActivityKt.isCompletionLogsSendAllowed();
  }
}
