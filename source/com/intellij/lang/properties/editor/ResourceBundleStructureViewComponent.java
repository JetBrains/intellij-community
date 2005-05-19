package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;

/**
 * @author cdr
 */
class ResourceBundleStructureViewComponent extends PropertiesGroupingStructureViewComponent {
  private final ResourceBundle myResourceBundle;

  public ResourceBundleStructureViewComponent(Project project, ResourceBundle resourceBundle, ResourceBundleEditor editor) {
    super(project, editor, new ResourceBundleStructureViewModel(project, resourceBundle));
    myResourceBundle = resourceBundle;
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.VIRTUAL_FILE)) {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    }
    return super.getData(dataId);
  }

  protected boolean showScrollToFromSourceActions() {
    return false;
  }
}

