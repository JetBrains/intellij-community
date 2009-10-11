/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
