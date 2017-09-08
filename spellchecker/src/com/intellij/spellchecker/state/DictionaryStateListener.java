package com.intellij.spellchecker.state;


import com.intellij.spellchecker.dictionary.EditableDictionary;

import java.util.EventListener;

public interface DictionaryStateListener extends EventListener {
  void dictChanged(EditableDictionary dictionary);
}
