package com.intellij.spellchecker.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.spellchecker.dictionary.UserDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;

@State(
  name = "CachedDictionaryState",
  storages = {@Storage(
    id = "spellchecker",
    file = "$APP_CONFIG$/cachedDictionary.xml")})
public class CachedDictionaryState implements PersistentStateComponent<CachedDictionaryState> {

  public UserDictionary dictionary = new UserDictionary("cached");

  public CachedDictionaryState getState() {
    return this;
  }

  public void loadState(CachedDictionaryState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public Dictionary getDictionary(){
    return dictionary;
  }
}
