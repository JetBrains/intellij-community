/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.spellchecker.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@State(name = "SpellCheckerSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SpellCheckerSettings implements PersistentStateComponent<Element> {
  // For xml serialization
  private static final String SPELLCHECKER_MANAGER_SETTINGS_TAG = "SpellCheckerSettings";

  private static final String FOLDERS_ATTR_NAME = "Folders";
  private static final String FOLDER_ATTR_NAME = "Folder";
  private static final String DICTIONARIES_ATTR_NAME = "Dictionaries";
  private static final String DICTIONARY_ATTR_NAME = "Dictionary";

  private static final String BUNDLED_DICTIONARIES_ATTR_NAME = "BundledDictionaries";
  private static final String BUNDLED_DICTIONARY_ATTR_NAME = "BundledDictionary";

  // Paths
  private List<String> myDictionaryFoldersPaths = new ArrayList<>();
  private Set<String> myDisabledDictionariesPaths = new HashSet<>();

  private Set<String> myBundledDisabledDictionariesPaths = new HashSet<>();

  public static SpellCheckerSettings getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerSettings.class);
  }


  public List<String> getDictionaryFoldersPaths() {
    return myDictionaryFoldersPaths;
  }

  public void setDictionaryFoldersPaths(List<String> dictionaryFoldersPaths) {
    myDictionaryFoldersPaths = dictionaryFoldersPaths;
  }

  public Set<String> getDisabledDictionariesPaths() {
    return myDisabledDictionariesPaths;
  }


  public void setDisabledDictionariesPaths(Set<String> disabledDictionariesPaths) {
    myDisabledDictionariesPaths = disabledDictionariesPaths;
  }


  public Set<String> getBundledDisabledDictionariesPaths() {
    return myBundledDisabledDictionariesPaths;
  }

  public void setBundledDisabledDictionariesPaths(Set<String> bundledDisabledDictionariesPaths) {
    myBundledDisabledDictionariesPaths = bundledDisabledDictionariesPaths;
  }

  @SuppressWarnings({"ConstantConditions"})
  public Element getState() {
    if (myBundledDisabledDictionariesPaths.isEmpty() &&
        myDictionaryFoldersPaths.isEmpty() &&
        myDisabledDictionariesPaths.isEmpty()) {
      return null;
    }

    final Element element = new Element(SPELLCHECKER_MANAGER_SETTINGS_TAG);
    // bundled
    element.setAttribute(BUNDLED_DICTIONARIES_ATTR_NAME, String.valueOf(myBundledDisabledDictionariesPaths.size()));
    Iterator<String> iterator = myBundledDisabledDictionariesPaths.iterator();
    int i = 0;
    while (iterator.hasNext()) {
      element.setAttribute(BUNDLED_DICTIONARY_ATTR_NAME + i, iterator.next());
      i++;
    }
    // user
    element.setAttribute(FOLDERS_ATTR_NAME, String.valueOf(myDictionaryFoldersPaths.size()));
    for (int j = 0; j < myDictionaryFoldersPaths.size(); j++) {
      element.setAttribute(FOLDER_ATTR_NAME + j, myDictionaryFoldersPaths.get(j));
    }
    element.setAttribute(DICTIONARIES_ATTR_NAME, String.valueOf(myDisabledDictionariesPaths.size()));
    iterator = myDisabledDictionariesPaths.iterator();
    i = 0;
    while (iterator.hasNext()) {
      element.setAttribute(DICTIONARY_ATTR_NAME + i, iterator.next());
      i++;
    }

    return element;
  }


  public void loadState(@NotNull final Element element) {
    myBundledDisabledDictionariesPaths.clear();
    myDictionaryFoldersPaths.clear();
    myDisabledDictionariesPaths.clear();
    try {
      // bundled
      final int bundledDictionariesSize = Integer.valueOf(element.getAttributeValue(BUNDLED_DICTIONARIES_ATTR_NAME));
      for (int i = 0; i < bundledDictionariesSize; i++) {
        myBundledDisabledDictionariesPaths.add(element.getAttributeValue(BUNDLED_DICTIONARY_ATTR_NAME + i));
      }
      // user
      final int foldersSize = Integer.valueOf(element.getAttributeValue(FOLDERS_ATTR_NAME));
      for (int i = 0; i < foldersSize; i++) {
        myDictionaryFoldersPaths.add(element.getAttributeValue(FOLDER_ATTR_NAME + i));
      }
      final int scriptsSize = Integer.valueOf(element.getAttributeValue(DICTIONARIES_ATTR_NAME));
      for (int i = 0; i < scriptsSize; i++) {
        myDisabledDictionariesPaths.add(element.getAttributeValue(DICTIONARY_ATTR_NAME + i));
      }
    }
    catch (Exception ignored) {
    }
  }
}
