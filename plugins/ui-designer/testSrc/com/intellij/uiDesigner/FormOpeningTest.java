// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.DumbUnawareHider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.util.ui.UIUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class FormOpeningTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("ui-designer") + "/testData");
  }

  @Test
  public void testOpenInDumbMode() {
    Project project = projectFixture.get();
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    FileEditorManagerEx editorManager = managerFixture.get();
    VirtualFile file = myFixture.copyFileToProject("TestBorder.form");
    GuiEditor editorComponent = DumbModeTestUtils.computeInDumbModeSynchronously(project, () -> EdtTestUtil.runInEdtAndGet(() -> {
      List<FileEditor> editors = editorManager.openFile(file, null, new FileEditorOpenOptions().withRequestFocus()).getAllEditors();
      assertEquals(1, editors.size());
      assertInstanceOf(UIFormEditor.class, editors.get(0));
      JComponent component = getEditorComponent(editorManager);
      assertInstanceOf(DumbUnawareHider.class, component);
      GuiEditor editor = UIUtil.uiTraverser(component).filter(GuiEditor.class).single();
      assertNotNull(editor, "editor not found");

      assertFalse(editor.isVisible());
      return editor;
    }));

    assertTrue(EdtTestUtil.runInEdtAndGet(() -> editorComponent.isVisible()));
  }

  private static JComponent getEditorComponent(FileEditorManagerEx editorManager) {
    EditorWindow window = editorManager.getSplitters().getCurrentWindow();
    return (JComponent)((JComponent)window.getSelectedComposite().getComponent().getComponents()[0]).getComponents()[0];
  }
}
