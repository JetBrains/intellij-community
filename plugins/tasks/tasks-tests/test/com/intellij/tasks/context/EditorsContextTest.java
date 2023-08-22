// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.context;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.ui.docking.DockManager;
import org.jdom.Element;

import static org.assertj.core.api.Assertions.assertThat;

@SkipInHeadlessEnvironment
public class EditorsContextTest extends FileEditorManagerTestCase {
  public void testDockableContainer() {
    DockManager dockManager = DockManager.getInstance(getProject());
    VirtualFile file = getFile("/foo.txt");
    manager.openFile(file, /* focusEditor = */ false);
    assertThat(dockManager.getContainers()).hasSize(1);
    manager.initDockableContentFactory();

    manager.openFileInNewWindow(file);
    assertThat(dockManager.getContainers()).hasSize(2);

    Element context = new Element("context");
    WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
    contextManager.saveContext(context);
    assertThat(context.getChild("editors").getChildren()).hasSize(2);
    assertThat(EditorFactory.getInstance().getAllEditors()).hasSize(2);

    contextManager.clearContext();
    assertThat(dockManager.getContainers()).hasSize(1);
    assertThat(EditorFactory.getInstance().getAllEditors()).isEmpty();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/tasks/tasks-tests/testData/context";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
