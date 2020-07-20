// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.stats.sender.SenderPreloadingActivityKt;
import com.jetbrains.completion.ranker.WeakModelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(name = "CompletionMLRankingSettings", storages = @Storage(value = "completionMLRanking.xml", roamingType = RoamingType.DISABLED))
public final class CompletionMLRankingSettings implements PersistentStateComponent<CompletionMLRankingSettings.State> {
  private static final Logger LOG = Logger.getInstance(CompletionMLRankingSettings.class);

  private final Collection<String> enabledByDefault = WeakModelProvider.enabledByDefault();
  private final State myState;

  public CompletionMLRankingSettings() {
    myState = new State();
    myState.rankingEnabled = !enabledByDefault.isEmpty();
  }

  @NotNull
  public static CompletionMLRankingSettings getInstance() {
    return ServiceManager.getService(CompletionMLRankingSettings.class);
  }

  public boolean isRankingEnabled() {
    return myState.rankingEnabled;
  }

  public boolean isShowDiffEnabled() {
    return myState.showDiff;
  }

  public void setRankingEnabled(boolean value) {
    if (value == isRankingEnabled()) return;
    myState.rankingEnabled = value;
    triggerSettingsChanged(value);
  }

  public boolean isCompletionLogsSendAllowed() {
    return SenderPreloadingActivityKt.isCompletionLogsSendAllowed();
  }

  public boolean isLanguageEnabled(@NotNull String languageName) {
    return myState.language2state.getOrDefault(languageName, isEnabledByDefault(languageName));
  }

  public void setLanguageEnabled(@NotNull String languageName, boolean isEnabled) {
    if (isEnabled == isLanguageEnabled(languageName)) return;
    setLanguageEnabledImpl(languageName, isEnabled);
    logCompletionState(languageName, isEnabled);
    if (isRankingEnabled()) {
      // log only if language ranking settings changes impact completion behavior
      MLCompletionSettingsCollector.rankingSettingsChanged(languageName, isEnabled, isEnabledByDefault(languageName), true);
    }
  }

  public void setShowDiffEnabled(boolean isEnabled) {
    if (isEnabled == isShowDiffEnabled()) return;
    myState.showDiff = isEnabled;
    MLCompletionSettingsCollector.decorationSettingChanged(isEnabled);
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.rankingEnabled = state.rankingEnabled;
    myState.showDiff = state.showDiff;
    state.language2state.forEach((lang, enabled) -> setLanguageEnabledImpl(lang, enabled));
  }

  private void logCompletionState(@NotNull String languageName, boolean isEnabled) {
    final boolean enabled = myState.rankingEnabled && isEnabled;
    final boolean showDiff = enabled && myState.showDiff;
    LOG.info("ML Completion " + (enabled ? "enabled" : "disabled") + " ,show diff " + (showDiff ? "on" : "off") + " for: " + languageName);
  }

  private boolean isEnabledByDefault(@NotNull String languageName) {
    return enabledByDefault.contains(languageName);
  }

  private void setLanguageEnabledImpl(@NotNull String languageName, boolean isEnabled) {
    if (isEnabledByDefault(languageName) == isEnabled) {
      myState.language2state.remove(languageName);
    }
    else {
      myState.language2state.put(languageName, isEnabled);
    }
  }

  private void triggerSettingsChanged(boolean enabled) {
    for (String ranker : getEnabledRankers()) {
      MLCompletionSettingsCollector.rankingSettingsChanged(ranker, enabled, isEnabledByDefault(ranker), false);
    }
  }

  private List<String> getEnabledRankers() {
    List<String> enabledRankers = new ArrayList<>(enabledByDefault);
    for (Map.Entry<String, Boolean> entry : myState.language2state.entrySet()) {
      String rankerId = entry.getKey();
      if (entry.getValue()) {
        enabledRankers.add(rankerId);
      }
      else {
        enabledRankers.remove(rankerId);
      }
    }

    return enabledRankers;
  }

  public static final class State {
    public boolean rankingEnabled;
    public boolean showDiff;
    // this map stores only different compare to default values to have ability to enable/disable models from build to build
    public final Map<String, Boolean> language2state = new HashMap<>();
  }
}
