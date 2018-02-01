// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.EventDispatcher;

@State(
  name = "CachedDictionaryState",
  storages = @Storage(value = "cachedDictionary.xml", roamingType = RoamingType.DISABLED)
)
public class CachedDictionaryState extends DictionaryState implements PersistentStateComponent<DictionaryState> {
  public static final String DEFAULT_NAME = "cached";
  private final EventDispatcher<DictionaryStateListener> myDictListenerEventDispatcher = EventDispatcher.create(DictionaryStateListener.class);

  public CachedDictionaryState() {
    name = DEFAULT_NAME;
  }

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

  public void addCachedDictListener(DictionaryStateListener listener) {
    myDictListenerEventDispatcher.addListener(listener);
  }
}
