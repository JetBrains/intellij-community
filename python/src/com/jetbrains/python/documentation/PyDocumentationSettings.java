package com.jetbrains.python.documentation;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(name = "PyDocumentationSettings",
      storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"),
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/other.xml", scheme = StorageScheme.DIRECTORY_BASED)
      }
)
public class PyDocumentationSettings implements PersistentStateComponent<PyDocumentationSettings> {
  public String myDocStringFormat = DocStringFormat.PLAIN;

  public boolean isEpydocFormat() {
    return DocStringFormat.EPYDOC.equals(myDocStringFormat);
  }

  public static PyDocumentationSettings getInstance(Project project) {
    return ServiceManager.getService(project, PyDocumentationSettings.class);
  }

  @Override
  public PyDocumentationSettings getState() {
    return this;
  }

  @Override
  public void loadState(PyDocumentationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
