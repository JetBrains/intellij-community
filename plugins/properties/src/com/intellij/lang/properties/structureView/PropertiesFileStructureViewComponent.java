/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.structureView;

import com.intellij.lang.properties.editor.PropertiesGroupingStructureViewComponent;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;

/**
 * @author cdr
 */
public class PropertiesFileStructureViewComponent extends PropertiesGroupingStructureViewComponent {
  private final PropertiesFile myPropertiesFile;

  public PropertiesFileStructureViewComponent(Project project, PropertiesFileImpl propertiesFile, FileEditor editor) {
    super(project, editor, new PropertiesFileStructureViewModel(propertiesFile, EditorUtil.getEditorEx(editor)));
    myPropertiesFile = propertiesFile;
  }

  @Override
  public Object getData(String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return myPropertiesFile.getVirtualFile();
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return myPropertiesFile;
    }
    return super.getData(dataId);
  }
}

