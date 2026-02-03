// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.spellchecker.ProjectDictionaryLayer;
import com.intellij.spellchecker.util.SPFileUtil;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.spellchecker.SpellCheckerManagerKt.isDic;

@State(name = "SpellCheckerSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class SpellCheckerSettings implements PersistentStateComponent<Element> {
  // For xml serialization
  private static final String SPELLCHECKER_MANAGER_SETTINGS_TAG = "SpellCheckerSettings";

  private static final String FOLDERS_ATTR_NAME = "Folders";
  private static final String FOLDER_ATTR_NAME = "Folder";

  private static final String CUSTOM_DICTIONARIES_ATTR_NAME = "CustomDictionaries";
  private static final String CUSTOM_DICTIONARY_ATTR_NAME = "CustomDictionary";

  private static final String RUNTIME_DICTIONARIES_ATTR_NAME = "RuntimeDictionaries";
  private static final String RUNTIME_DICTIONARY_ATTR_NAME = "RuntimeDictionary";

  private static final String DICTIONARY_TO_SAVE_ATTR_NAME = "DefaultDictionary";
  private static final String DEFAULT_DICTIONARY_TO_SAVE = ProjectDictionaryLayer.Companion.getName().get();
  private static final String USE_SINGLE_DICT_ATTR_NAME = "UseSingleDictionary";
  private static final boolean DEFAULT_USE_SINGLE_DICT = false;
  private static final String SETTINGS_TRANSFERRED = "transferred";

  // Paths
  private final List<String> myOldDictionaryFoldersPaths = new ArrayList<>();
  private List<String> myCustomDictionariesPaths = new ArrayList<>();

  private Set<String> myRuntimeDisabledDictionariesNames = new HashSet<>();
  private String myDictionaryToSave = DEFAULT_DICTIONARY_TO_SAVE;
  private boolean myUseSingleDictionaryToSave = DEFAULT_USE_SINGLE_DICT;
  private boolean mySettingsTransferred;

  public @NlsSafe String getDictionaryToSave() {
    //This is NLS safe since dictionary names are NLS
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

  public boolean isSettingsTransferred() {
    return mySettingsTransferred;
  }

  public void setSettingsTransferred(boolean settingsTransferred) {
    mySettingsTransferred = settingsTransferred;
  }

  public static @NotNull SpellCheckerSettings getInstance(Project project) {
    return project.getService(SpellCheckerSettings.class);
  }

  public List<String> getCustomDictionariesPaths() {
    return myCustomDictionariesPaths;
  }

  public void setCustomDictionariesPaths(List<String> customDictionariesPaths) {
    myCustomDictionariesPaths = customDictionariesPaths;
  }

  public Set<String> getRuntimeDisabledDictionariesNames() {
    return myRuntimeDisabledDictionariesNames;
  }

  public void setRuntimeDisabledDictionariesNames(Set<String> runtimeDisabledDictionariesNames) {
    myRuntimeDisabledDictionariesNames = runtimeDisabledDictionariesNames;
  }

  @Override
  public Element getState() {
    if (myRuntimeDisabledDictionariesNames.isEmpty() &&
        myOldDictionaryFoldersPaths.isEmpty() &&
        myCustomDictionariesPaths.isEmpty() &&
        myUseSingleDictionaryToSave == DEFAULT_USE_SINGLE_DICT &&
        myDictionaryToSave.equals(DEFAULT_DICTIONARY_TO_SAVE) && !mySettingsTransferred) {
      return null;
    }

    final Element element = new Element(SPELLCHECKER_MANAGER_SETTINGS_TAG);
    // runtime
    element.setAttribute(RUNTIME_DICTIONARIES_ATTR_NAME, String.valueOf(myRuntimeDisabledDictionariesNames.size()));
    Iterator<String> iterator  = myRuntimeDisabledDictionariesNames.iterator();
    int i = 0;
    while (iterator.hasNext()) {
      element.setAttribute(RUNTIME_DICTIONARY_ATTR_NAME + i, iterator.next());
      i++;
    }
    // user
    // save custom dictionaries parents because of back compatibility
    element.setAttribute(FOLDERS_ATTR_NAME, String.valueOf(myCustomDictionariesPaths.size()));
    for (int j = 0; j < myCustomDictionariesPaths.size(); j++) {
      final var parentPath = PathUtil.getParentPath(myCustomDictionariesPaths.get(j));
      element.setAttribute(FOLDER_ATTR_NAME + j, parentPath);
    }
    // store new dictionaries settings
    element.setAttribute(CUSTOM_DICTIONARIES_ATTR_NAME, String.valueOf(myCustomDictionariesPaths.size()));
    for (int j = 0; j < myCustomDictionariesPaths.size(); j++) {
      element.setAttribute(CUSTOM_DICTIONARY_ATTR_NAME + j, myCustomDictionariesPaths.get(j));
    }
    element.setAttribute(DICTIONARY_TO_SAVE_ATTR_NAME, myDictionaryToSave);
    element.setAttribute(USE_SINGLE_DICT_ATTR_NAME, String.valueOf(myUseSingleDictionaryToSave));
    element.setAttribute(SETTINGS_TRANSFERRED, String.valueOf(mySettingsTransferred));
    return element;
  }


  @Override
  public void loadState(final @NotNull Element element) {
    myRuntimeDisabledDictionariesNames.clear();
    myCustomDictionariesPaths.clear();
    myOldDictionaryFoldersPaths.clear();
    try {
      // runtime
      final int runtimeDictionariesSize = StringUtil.parseInt(element.getAttributeValue(RUNTIME_DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < runtimeDictionariesSize; i++) {
        myRuntimeDisabledDictionariesNames.add(element.getAttributeValue(RUNTIME_DICTIONARY_ATTR_NAME + i));
      }
      // user
      // cover old dictionary folders settings (if no new settings available)
      if (element.getAttributeValue(CUSTOM_DICTIONARIES_ATTR_NAME) == null) {
        final int foldersSize = StringUtil.parseInt(element.getAttributeValue(FOLDERS_ATTR_NAME), 0);
        for (int i = 0; i < foldersSize; i++) {
          myOldDictionaryFoldersPaths.add(element.getAttributeValue(FOLDER_ATTR_NAME + i));
        }
        myOldDictionaryFoldersPaths.forEach(folder -> SPFileUtil.processFilesRecursively(folder, file -> {
          if (isDic(file)) {
            myCustomDictionariesPaths.add(file);
          }
        }));
      }
      // cover new dictionaries settings
      final int customDictSize = StringUtil.parseInt(element.getAttributeValue(CUSTOM_DICTIONARIES_ATTR_NAME), 0);
      for (int i = 0; i < customDictSize; i++) {
        myCustomDictionariesPaths.add(element.getAttributeValue(CUSTOM_DICTIONARY_ATTR_NAME + i));
      }
      myDictionaryToSave = StringUtil.notNullize(element.getAttributeValue(DICTIONARY_TO_SAVE_ATTR_NAME), DEFAULT_DICTIONARY_TO_SAVE);
      myUseSingleDictionaryToSave =
        Boolean.parseBoolean(
          StringUtil.notNullize(element.getAttributeValue(USE_SINGLE_DICT_ATTR_NAME), String.valueOf(DEFAULT_USE_SINGLE_DICT)));
      mySettingsTransferred = Boolean.parseBoolean(StringUtil.notNullize(element.getAttributeValue(SETTINGS_TRANSFERRED), "false"));
    }
    catch (Exception ignored) {
    }
  }
}
