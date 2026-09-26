// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.state;

import com.intellij.spellchecker.dictionary.EditableDictionary;

import java.util.EventListener;

public interface DictionaryStateListener extends EventListener {
  void dictChanged(EditableDictionary dictionary);
}
