// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
@State(name = "PyCodeInsightSettings", storages = @Storage("other.xml"))
public class PyCodeInsightSettings implements PersistentStateComponent<PyCodeInsightSettings> {
  public static PyCodeInsightSettings getInstance() {
    return ApplicationManager.getApplication().getService(PyCodeInsightSettings.class);
  }

  public boolean PREFER_FROM_IMPORT = true;
  public boolean SHOW_IMPORT_POPUP;
  public boolean HIGHLIGHT_UNUSED_IMPORTS = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  public boolean RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
  public boolean RENAME_SEARCH_NON_CODE_FOR_CLASS;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  public boolean RENAME_SEARCH_NON_CODE_FOR_VARIABLE;

  public boolean DJANGO_AUTOINSERT_TAG_CLOSE = true;

  public boolean RENAME_CLASS_CONTAINING_FILE = true;
  public boolean RENAME_CLASS_INHERITORS = true;
  public boolean RENAME_PARAMETERS_IN_HIERARCHY = true;

  public boolean INSERT_BACKSLASH_ON_WRAP = true;
  public boolean INSERT_SELF_FOR_METHODS = true;

  public boolean INSERT_TYPE_DOCSTUB;

  @Override
  public PyCodeInsightSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
