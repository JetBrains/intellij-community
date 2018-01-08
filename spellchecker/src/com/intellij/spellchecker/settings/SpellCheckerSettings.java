/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.spellchecker.util.SPFileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.util.text.StringUtil.parseInt;

@State(name = "SpellCheckerSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class SpellCheckerSettings implements PersistentStateComponent<Element> {
  // For xml serialization
  private static final String SPELLCHECKER_MANAGER_SETTINGS_TAG = "SpellCheckerSettings";

  private static final String FOLDERS_ATTR_NAME = "Folders";
  private static final String FOLDER_ATTR_NAME = "Folder";
  private static final String CUSTOM_DICTIONARIES_ATTR_NAME = "CustomDictionaries";
  private static final String CUSTOM_DICTIONARY_ATTR_NAME = "CustomDictionary";
  private static final String DICTIONARIES_ATTR_NAME = "Dictionaries";
  private static final String DICTIONARY_ATTR_NAME = "Dictionary";

  private static final String BUNDLED_DICTIONARIES_ATTR_NAME = "BundledDictionaries";
  private static final String BUNDLED_DICTIONARY_ATTR_NAME = "BundledDictionary";

  // Paths
  private List<String> myOldDictionaryFoldersPaths = new ArrayList<>();
  private List<String> myCustomDictionariesPaths = new ArrayList<>();
  private Set<String> myDisabledDictionariesPaths = new HashSet<>();

  private Set<String> myBundledDisabledDictionariesPaths = new HashSet<>();

  public static SpellCheckerSettings getInstance(Project project) {
    return ServiceManager.getService(project, SpellCheckerSettings.class);
  }

  public List<String> getCustomDictionariesPaths() {
    return myCustomDictionariesPaths;
  }

  public void setCustomDictionariesPaths(List<String> customDictionariesPaths) {
    myCustomDictionariesPaths = customDictionariesPaths;
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

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public Element getState() {
    if (myBundledDisabledDictionariesPaths.isEmpty() &&
        myOldDictionaryFoldersPaths.isEmpty() &&
        myCustomDictionariesPaths.isEmpty() &&
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
    // remove old dictionary folder settings
    element.removeAttribute(FOLDERS_ATTR_NAME);
    for (int j = 0; j < myOldDictionaryFoldersPaths.size(); j++) {
      element.removeAttribute(FOLDER_ATTR_NAME + j);
    }
    // store new dictionaries settings instead
    element.setAttribute(CUSTOM_DICTIONARIES_ATTR_NAME, String.valueOf(myCustomDictionariesPaths.size()));
    for (int j = 0; j < myCustomDictionariesPaths.size(); j++) {
      element.setAttribute(CUSTOM_DICTIONARY_ATTR_NAME + j, myCustomDictionariesPaths.get(j));
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


  @Override
  public void loadState(@NotNull final Element element) {
    myBundledDisabledDictionariesPaths.clear();
    myCustomDictionariesPaths.clear();
    myOldDictionaryFoldersPaths.clear();
    myDisabledDictionariesPaths.clear();
    try {
      // bundled
      final int bundledDictionariesSize = parseInt(element.getAttributeValue(BUNDLED_DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < bundledDictionariesSize; i++) {
        myBundledDisabledDictionariesPaths.add(element.getAttributeValue(BUNDLED_DICTIONARY_ATTR_NAME + i));
      }
      // user
      // cover old dictionary folders settings
      final int foldersSize = parseInt(element.getAttributeValue(FOLDERS_ATTR_NAME), 0);
      for (int i = 0; i < foldersSize; i++) {
        myOldDictionaryFoldersPaths.add(element.getAttributeValue(FOLDER_ATTR_NAME + i));
      }
      myOldDictionaryFoldersPaths.forEach(folder -> SPFileUtil.processFilesRecursively(folder, file -> {
        if(extensionEquals(file, "dic")){
          myCustomDictionariesPaths.add(file);
        }
      }));
      // cover new dictionaries settings
      final int customDictSize = parseInt(element.getAttributeValue(CUSTOM_DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < customDictSize; i++) {
        myCustomDictionariesPaths.add(element.getAttributeValue(CUSTOM_DICTIONARY_ATTR_NAME + i));
      }
      final int scriptsSize = parseInt(element.getAttributeValue(DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < scriptsSize; i++) {
        myDisabledDictionariesPaths.add(element.getAttributeValue(DICTIONARY_ATTR_NAME + i));
      }
    }
    catch (Exception ignored) {
    }
  }
}
