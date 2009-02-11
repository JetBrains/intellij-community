package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.editor.PropertiesGroupingStructureViewComponent;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;

/**
 * @author cdr
 */
public class PropertiesFileStructureViewComponent extends PropertiesGroupingStructureViewComponent {
  private final PropertiesFile myPropertiesFile;

  public PropertiesFileStructureViewComponent(Project project, PropertiesFile propertiesFile, FileEditor editor) {
    super(project, editor, new PropertiesFileStructureViewModel(propertiesFile));
    myPropertiesFile = propertiesFile;
  }

  public Object getData(String dataId) {
    if (dataId.equals(DataConstants.VIRTUAL_FILE)) {
      return myPropertiesFile.getVirtualFile();
    }
    if (dataId.equals(DataConstants.PSI_ELEMENT)) {
      return myPropertiesFile;
    }
    return super.getData(dataId);
  }
}

