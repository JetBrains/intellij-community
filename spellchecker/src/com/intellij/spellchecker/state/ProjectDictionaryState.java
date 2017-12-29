/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.ProjectDictionary;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@State(name = "ProjectDictionaryState", storages = @Storage(value = "dictionaries", stateSplitter = ProjectDictionarySplitter.class))
public class ProjectDictionaryState implements PersistentStateComponent<ProjectDictionaryState> {
  @Property(surroundWithTag = false)
  @XCollection(elementTypes = DictionaryState.class)
  public List<DictionaryState> dictionaryStates = new ArrayList<>();

  private ProjectDictionary projectDictionary;

  private final EventDispatcher<DictionaryStateListener> myDictListenerEventDispatcher = EventDispatcher.create(DictionaryStateListener.class);

  public ProjectDictionaryState() {
  }

  @Transient
  public void setProjectDictionary(ProjectDictionary projectDictionary) {
    dictionaryStates.clear();
    Set<EditableDictionary> projectDictionaries = projectDictionary.getDictionaries();
    if (projectDictionaries != null) {
      for (EditableDictionary dic : projectDictionary.getDictionaries()) {
        dictionaryStates.add(new DictionaryState(dic));
      }
    }
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
  public void loadState(ProjectDictionaryState state) {
    if (state != null) {
      this.dictionaryStates = state.dictionaryStates;
    }
    retrieveProjectDictionaries();
  }

  private void retrieveProjectDictionaries() {
    Set<EditableDictionary> dictionaries = new THashSet<>();
    if (dictionaryStates != null) {
      for (DictionaryState dictionaryState : dictionaryStates) {
        dictionaryState.loadState(dictionaryState);
        dictionaries.add(dictionaryState.getDictionary());
      }
    }
    projectDictionary = new ProjectDictionary(dictionaries);
    myDictListenerEventDispatcher.getMulticaster().dictChanged(projectDictionary);
  }

  @Override
  public String toString() {
    return "ProjectDictionaryState{" + "projectDictionary=" + projectDictionary + '}';
  }

  public void addProjectDictListener(DictionaryStateListener listener) {
    myDictListenerEventDispatcher.addListener(listener);
  }
}
