// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.util.SPFileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.util.text.StringUtil.*;

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
  private static final String CORRECTIONS_MAX_LIMIT = "CorrectionsLimit";
  private static final int DEFAULT_MAX_VALUE = 5;
  private static final String DICTIONARY_TO_SAVE_ATTR_NAME = "DefaultDictionary";
  private static final String DEFAULT_DICTIONARY_TO_SAVE = SpellCheckerManager.DictionaryLevel.PROJECT.getName();
  private static final String USE_SINGLE_DICT_ATTR_NAME = "UseSingleDictionary";
  private static final boolean DEFAULT_USE_SINGLE_DICT = true;

  // Paths
  private final List<String> myOldDictionaryFoldersPaths = new ArrayList<>();
  private List<String> myCustomDictionariesPaths = new ArrayList<>();
  private Set<String> myDisabledDictionariesPaths = new HashSet<>();

  private Set<String> myBundledDisabledDictionariesPaths = new HashSet<>();
  private int myCorrectionsLimit = DEFAULT_MAX_VALUE;
  private String myDictionaryToSave = DEFAULT_DICTIONARY_TO_SAVE;
  private boolean myUseSingleDictionaryToSave = DEFAULT_USE_SINGLE_DICT;

  public int getCorrectionsLimit() {
    return myCorrectionsLimit;
  }

  public void setCorrectionsLimit(int correctionsLimit) {
    myCorrectionsLimit = correctionsLimit;
  }

  public String getDictionaryToSave() {
    return myDictionaryToSave;
  }

  public void setDictionaryToSave(String dictionaryToSave) {
    myDictionaryToSave = dictionaryToSave;
  }

  public boolean isUseSingleDictionaryToSave() {
    return myUseSingleDictionaryToSave;
  }

  public void setUseSingleDictionaryToSave(boolean useSingleDictionaryToSave) {
    this.myUseSingleDictionaryToSave = useSingleDictionaryToSave;
  }

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

  public boolean isDefaultAdvancedSettings(){
    return myCorrectionsLimit == DEFAULT_MAX_VALUE &&
           myUseSingleDictionaryToSave == DEFAULT_USE_SINGLE_DICT &&
           myDictionaryToSave == DEFAULT_DICTIONARY_TO_SAVE;
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public Element getState() {
    if (myBundledDisabledDictionariesPaths.isEmpty() &&
        myOldDictionaryFoldersPaths.isEmpty() &&
        myCustomDictionariesPaths.isEmpty() &&
        myDisabledDictionariesPaths.isEmpty() &&
        myCorrectionsLimit == DEFAULT_MAX_VALUE &&
        myUseSingleDictionaryToSave == DEFAULT_USE_SINGLE_DICT &&
        myDictionaryToSave.equals(DEFAULT_DICTIONARY_TO_SAVE)) {
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
    // save custom dictionaries parents because of back compatibility
    element.setAttribute(FOLDERS_ATTR_NAME, String.valueOf(myCustomDictionariesPaths.size()));
    for (int j = 0; j < myCustomDictionariesPaths.size(); j++) {
      element.setAttribute(FOLDER_ATTR_NAME + j, Paths.get(myCustomDictionariesPaths.get(j)).getParent().toString());
    }
    // store new dictionaries settings
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
    element.setAttribute(CORRECTIONS_MAX_LIMIT, String.valueOf(myCorrectionsLimit));
    element.setAttribute(DICTIONARY_TO_SAVE_ATTR_NAME, myDictionaryToSave);
    element.setAttribute(USE_SINGLE_DICT_ATTR_NAME, String.valueOf(myUseSingleDictionaryToSave));
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
      // cover old dictionary folders settings (if no new settings available)
      if (element.getAttributeValue(CUSTOM_DICTIONARIES_ATTR_NAME) == null) {
        final int foldersSize = parseInt(element.getAttributeValue(FOLDERS_ATTR_NAME), 0);
        for (int i = 0; i < foldersSize; i++) {
          myOldDictionaryFoldersPaths.add(element.getAttributeValue(FOLDER_ATTR_NAME + i));
        }
        myOldDictionaryFoldersPaths.forEach(folder -> SPFileUtil.processFilesRecursively(folder, file -> {
          if (extensionEquals(file, "dic")) {
            myCustomDictionariesPaths.add(file);
          }
        }));
      }
      // cover new dictionaries settings
      final int customDictSize = parseInt(element.getAttributeValue(CUSTOM_DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < customDictSize; i++) {
        myCustomDictionariesPaths.add(element.getAttributeValue(CUSTOM_DICTIONARY_ATTR_NAME + i));
      }
      final int scriptsSize = parseInt(element.getAttributeValue(DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < scriptsSize; i++) {
        myDisabledDictionariesPaths.add(element.getAttributeValue(DICTIONARY_ATTR_NAME + i));
      }
      myCorrectionsLimit = parseInt(element.getAttributeValue(CORRECTIONS_MAX_LIMIT), DEFAULT_MAX_VALUE);
      myDictionaryToSave = notNullize(element.getAttributeValue(DICTIONARY_TO_SAVE_ATTR_NAME), DEFAULT_DICTIONARY_TO_SAVE);
      myUseSingleDictionaryToSave =
        parseBoolean(notNullize(element.getAttributeValue(USE_SINGLE_DICT_ATTR_NAME), String.valueOf(DEFAULT_USE_SINGLE_DICT)),
                     DEFAULT_USE_SINGLE_DICT);
    }
    catch (Exception ignored) {
    }
  }
}
