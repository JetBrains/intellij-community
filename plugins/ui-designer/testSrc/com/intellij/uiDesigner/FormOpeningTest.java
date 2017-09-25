/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.uiDesigner;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.DumbUnawareHider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class FormOpeningTest extends FileEditorManagerTestCase {

  public void testOpenInDumbMode() {

    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(getProject());
    VirtualFile file = myFixture.copyFileToProject("TestBorder.form");
    DumbServiceImpl dumbService = (DumbServiceImpl)DumbService.getInstance(getProject());
    dumbService.setDumb(true);
    try {
      FileEditor[] editors = editorManager.openFile(file, true);
      assertEquals(1, editors.length);
      assertInstanceOf(editors[0], UIFormEditor.class);
      JComponent component = getEditorComponent();
      assertInstanceOf(component, DumbUnawareHider.class);
      assertEquals(2, component.getComponentCount());

      Component editorComponent = component.getComponents()[0];
      assertInstanceOf(editorComponent, GuiEditor.class);
      assertFalse(editorComponent.isVisible());

      dumbService.setDumb(false);
      assertTrue(editorComponent.isVisible());
    }
    finally {
      dumbService.setDumb(false);
    }
  }

  private JComponent getEditorComponent() {
    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(getProject());
    EditorWindow window = editorManager.getSplitters().getCurrentWindow();
    return (JComponent)((JComponent)window.getSelectedEditor().getComponent().getComponents()[0]).getComponents()[0];
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }
}
