// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.idea.IJIgnore;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.FileEditorManagerTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.docking.DockManager;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.fileEditorManagerFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;

@IJIgnore(issue = "AT-3959")
@SkipInHeadlessEnvironment
@TestApplication
public class EditorsContextTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<FileEditorManagerImpl> managerFixture = fileEditorManagerFixture(projectFixture, false);

  @Test
  public void testDockableContainer() {
    Project project = projectFixture.get();
    FileEditorManagerImpl manager = managerFixture.get();
    DockManager dockManager = DockManager.getInstance(project);
    VirtualFile file = FileEditorManagerTestUtil.getFile(getTestDataPath(), "/foo.txt");
    EdtTestUtil.runInEdtAndWait(() -> manager.openFile(file, /* focusEditor = */ false));
    assertThat(dockManager.getContainers()).hasSize(1);

    EdtTestUtil.runInEdtAndWait(() -> {
      manager.initDockableContentFactory();
      manager.openFileInNewWindow(file);
    });
    assertThat(dockManager.getContainers()).hasSize(2);

    Element context = new Element("context");
    WorkingContextManager contextManager = WorkingContextManager.getInstance(project);
    EdtTestUtil.runInEdtAndWait(() -> contextManager.saveContext(context));
    assertThat(context.getChild("editors").getChildren()).hasSize(2);
    assertThat(EditorFactory.getInstance().getAllEditors()).hasSize(2);

    EdtTestUtil.runInEdtAndWait(() -> contextManager.clearContext());
    assertThat(dockManager.getContainers()).hasSize(1);
    assertThat(EditorFactory.getInstance().getAllEditors()).isEmpty();
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace('\\', '/') + "/plugins/tasks/tests/base/testData/context";
  }
}
