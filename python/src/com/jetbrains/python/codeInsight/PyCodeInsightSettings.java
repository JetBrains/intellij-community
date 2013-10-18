package com.jetbrains.python.codeInsight;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(
  name="PyCodeInsightSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
public class PyCodeInsightSettings implements PersistentStateComponent<PyCodeInsightSettings> {
  public static PyCodeInsightSettings getInstance() {
    return ServiceManager.getService(PyCodeInsightSettings.class);
  }

  public boolean PREFER_FROM_IMPORT = true;
  public boolean SHOW_IMPORT_POPUP = false;
  public boolean HIGHLIGHT_UNUSED_IMPORTS = true;

  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_FUNCTION = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_CLASS = false;
  public boolean RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = false;
  public boolean RENAME_SEARCH_NON_CODE_FOR_VARIABLE = false;

  public boolean DJANGO_AUTOINSERT_TAG_CLOSE = true;

  public boolean RENAME_CLASS_CONTAINING_FILE = true;
  public boolean RENAME_CLASS_INHERITORS = true;
  public boolean RENAME_PARAMETERS_IN_HIERARCHY = true;

  public boolean INSERT_BACKSLASH_ON_WRAP = true;
  public boolean INSERT_SELF_FOR_METHODS = true;

  public boolean INSERT_TYPE_DOCSTUB = false;

  public PyCodeInsightSettings getState() {
    return this;
  }

  public void loadState(PyCodeInsightSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
