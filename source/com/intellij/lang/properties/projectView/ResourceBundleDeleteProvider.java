package com.intellij.lang.properties.projectView;

import com.intellij.ide.DeleteProvider;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;

import java.util.List;

/**
 * @author cdr
 */
class ResourceBundleDeleteProvider implements DeleteProvider {
  private final ResourceBundle myResourceBundle;

  public ResourceBundleDeleteProvider(ResourceBundle resourceBundle) {
    myResourceBundle = resourceBundle;
  }

  public void deleteElement(DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(project);
    PropertiesFile[] array = propertiesFiles.toArray(new PropertiesFile[propertiesFiles.size()]);
    new SafeDeleteHandler().invoke(project, array, dataContext);
  }

  public boolean canDeleteElement(DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(dataContext);
    return project != null;
  }
}
