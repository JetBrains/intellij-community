// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.refactoring;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "PropertiesRefactoringSettings", storages = @Storage("baseRefactoring.xml"))
public class PropertiesRefactoringSettings implements PersistentStateComponent<PropertiesRefactoringSettings> {
  public boolean RENAME_SEARCH_IN_COMMENTS = false;

  public static PropertiesRefactoringSettings getInstance() {
    return ServiceManager.getService(PropertiesRefactoringSettings.class);
  }

  public PropertiesRefactoringSettings getState() {
    return this;
  }

  public void loadState(@NotNull PropertiesRefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}