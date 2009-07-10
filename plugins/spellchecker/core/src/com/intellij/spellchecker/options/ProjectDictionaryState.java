package com.intellij.spellchecker.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.spellchecker.dictionary.UserDictionary;
import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.ProjectDictionary;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "ProjectDictionaryState",
  storages = {@Storage(
    id = "other",
    file = "$PROJECT_FILE$"), @Storage(
    id = "dir",
    file = "$PROJECT_CONFIG_DIR$/dictionaries/",
    scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ProjectDictionarySplitter.class)})

public class ProjectDictionaryState implements PersistentStateComponent<ProjectDictionaryState> {

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false,elementTypes = UserDictionary.class)
  public List<Dictionary> dictionaries = new ArrayList<Dictionary>();


  private ProjectDictionary projectDictionary;

 

  public ProjectDictionaryState getState() {
    return this;
  }


  public void loadState(ProjectDictionaryState state) {
    XmlSerializerUtil.copyBean(state, this);
    createProjectDictionary();
  }

  private void createProjectDictionary() {
    projectDictionary = new ProjectDictionary("project");
    projectDictionary.setDictionaries(dictionaries);
    projectDictionary.getUserDictionary();
  }

  public ProjectDictionary getDictionary() {
    if (projectDictionary == null) {
      createProjectDictionary();
    }
    return projectDictionary;
  }
}

