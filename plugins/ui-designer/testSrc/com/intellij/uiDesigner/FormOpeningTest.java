// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbUnawareHider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class FormOpeningTest extends FileEditorManagerTestCase {

  public void testOpenInDumbMode() {

    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(getProject());
    VirtualFile file = myFixture.copyFileToProject("TestBorder.form");
    GuiEditor editorComponent = DumbModeTestUtils.computeInDumbModeSynchronously(getProject(), () -> {
      FileEditor[] editors = editorManager.openFile(file, true);
      assertEquals(1, editors.length);
      assertInstanceOf(editors[0], UIFormEditor.class);
      JComponent component = getEditorComponent();
      assertInstanceOf(component, DumbUnawareHider.class);
      GuiEditor editor = UIUtil.uiTraverser(component).filter(GuiEditor.class).single();
      assertNotNull("editor not found", editor);

      assertFalse(editor.isVisible());
      return editor;
    });

    assertTrue(editorComponent.isVisible());
  }

  private JComponent getEditorComponent() {
    FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(getProject());
    EditorWindow window = editorManager.getSplitters().getCurrentWindow();
    return (JComponent)((JComponent)window.getSelectedComposite().getComponent().getComponents()[0]).getComponents()[0];
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData";
  }
}
