package com.intellij.spellchecker.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.HashSet;
import java.util.Set;

@State(
  name = "SpellChecker",
  storages = {@Storage(
    id = "spellchecker",
    file = "$APP_CONFIG$/spellchecker.xml")})
public final class SpellCheckerConfiguration implements PersistentStateComponent<SpellCheckerConfiguration> {
  public Set<String> USER_DICTIONARY_WORDS = new HashSet<String>();
  public Set<String> IGNORED_WORDS = new HashSet<String>();

  public SpellCheckerConfiguration getState() {
    return this;
  }

  public void loadState(SpellCheckerConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
