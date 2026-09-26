// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.ProjectDictionary;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
@State(name = "ProjectDictionaryState", storages = @Storage(value = "dictionaries", stateSplitter = ProjectDictionarySplitter.class))
public final class ProjectDictionaryState implements PersistentStateComponent<ProjectDictionaryState> {
  @Property(surroundWithTag = false)
  @XCollection(elementTypes = DictionaryState.class)
  public @Unmodifiable List<DictionaryState> dictionaryStates = new ArrayList<>();

  private ProjectDictionary projectDictionary;

  private final EventDispatcher<DictionaryStateListener> dictListenerEventDispatcher =
    EventDispatcher.create(DictionaryStateListener.class);

  @Transient
  public void setProjectDictionary(ProjectDictionary projectDictionary) {
    List<DictionaryState> dictionaryStates = new ArrayList<>();
    Set<EditableDictionary> projectDictionaries = projectDictionary.getDictionaries();
    if (projectDictionaries != null) {
      for (EditableDictionary dic : projectDictionary.getDictionaries()) {
        dictionaryStates.add(new DictionaryState(dic));
      }
    }
    this.dictionaryStates = dictionaryStates;
  }

  @Transient
  public ProjectDictionary getProjectDictionary() {
    if (projectDictionary == null) {
      projectDictionary = new ProjectDictionary();
    }
    return projectDictionary;
  }

  @Override
  public ProjectDictionaryState getState() {
    if (projectDictionary != null) {
      //ensure all dictionaries within project dictionary will be stored
      setProjectDictionary(projectDictionary);
    }
    return this;
  }

  @Override
  public void loadState(@NotNull ProjectDictionaryState state) {
    this.dictionaryStates = state.dictionaryStates;
    retrieveProjectDictionaries();
  }

  private void retrieveProjectDictionaries() {
    Set<EditableDictionary> dictionaries = ConcurrentHashMap.newKeySet();
    List<DictionaryState> dictionaryStates = this.dictionaryStates;
    if (dictionaryStates != null) {
      for (DictionaryState dictionaryState : dictionaryStates) {
        dictionaryState.loadState(dictionaryState);
        dictionaries.add(dictionaryState.getDictionary());
      }
    }
    projectDictionary = new ProjectDictionary(dictionaries);
    dictListenerEventDispatcher.getMulticaster().dictChanged(projectDictionary);
  }

  @Override
  public String toString() {
    return "ProjectDictionaryState{" + "projectDictionary=" + projectDictionary + '}';
  }

  public void addProjectDictListener(DictionaryStateListener listener) {
    dictListenerEventDispatcher.addListener(listener);
  }
}
