package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

class ProjectStateStorageManager extends StateStorageManagerImpl {
  private Project myProject;
  @NonNls private static final String ROOT_TAG_NAME = "project";

  public ProjectStateStorageManager(final TrackingPathMacroSubstitutor macroSubstitutor, Project project) {
    super(macroSubstitutor, ROOT_TAG_NAME, project, project.getPicoContainer());
    myProject = project;
  }

  protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
    if (storageSpec.equals(ProjectStoreImpl.PROJECT_FILE_STORAGE)) return new ProjectStoreImpl.IprStorageData(ROOT_TAG_NAME, myProject);
    if (storageSpec.equals(ProjectStoreImpl.WS_FILE_STORAGE)) return new ProjectStoreImpl.WsStorageData(ROOT_TAG_NAME, myProject);
    return new ProjectStoreImpl.ProjectStorageData(ROOT_TAG_NAME, myProject);
  }

  protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) throws
                                                                                                                              StateStorage.StateStorageException {
    final ComponentConfig config = myProject.getConfig(component.getClass());
    assert config != null : "Couldn't find old storage for " + component.getClass().getName();

    String macro = ProjectStoreImpl.PROJECT_FILE_MACRO;

    final boolean workspace = isWorkspace(config.options);

    if (workspace) {
      macro = ProjectStoreImpl.WS_FILE_MACRO;
    }

    String name = "$" + macro + "$";

    StateStorage storage = getFileStateStorage(name);

    if (operation == StateStorageOperation.READ && storage != null && workspace && !storage.hasState(component, componentName, Element.class)) {
      name = "$" + ProjectStoreImpl.PROJECT_FILE_MACRO + "$";
    }

    return name;
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(ProjectStoreImpl.OPTION_WORKSPACE));
  }
}
