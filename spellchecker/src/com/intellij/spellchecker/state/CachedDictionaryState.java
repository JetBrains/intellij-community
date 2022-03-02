// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.state;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

@State(
  name = "CachedDictionaryState",
  storages = @Storage(value = StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED),
  reportStatistic = false
)
@Service(Service.Level.APP)
public final class CachedDictionaryState extends DictionaryState implements PersistentStateComponent<DictionaryState> {
  public static final String DEFAULT_NAME = "cached";
  private final EventDispatcher<DictionaryStateListener> myDictListenerEventDispatcher =
    EventDispatcher.create(DictionaryStateListener.class);

  public CachedDictionaryState() {
    name = DEFAULT_NAME;
  }

  public static @NotNull CachedDictionaryState getInstance() {
    return ApplicationManager.getApplication().getService(CachedDictionaryState.class);
  }

  @NonInjectable
  public CachedDictionaryState(EditableDictionary dictionary) {
    super(dictionary);
    name = DEFAULT_NAME;
  }

  @Override
  public void loadState(@NotNull DictionaryState state) {
    if (state.name == null) {
      state.name = DEFAULT_NAME;
    }
    super.loadState(state);
    myDictListenerEventDispatcher.getMulticaster().dictChanged(getDictionary());
  }

  public void addCachedDictListener(DictionaryStateListener listener, Disposable parentDisposable) {
    myDictListenerEventDispatcher.addListener(listener, parentDisposable);
  }
}
