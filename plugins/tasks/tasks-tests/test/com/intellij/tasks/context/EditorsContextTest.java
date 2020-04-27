// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.ui.docking.DockManager;
import org.jdom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
@SkipInHeadlessEnvironment
public class EditorsContextTest extends FileEditorManagerTestCase {
  public void testDockableContainer() {
    VirtualFile file = getFile("/foo.txt");
    myManager.openFile(file, /* focusEditor = */ false);
    DockManager dockManager = DockManager.getInstance(getProject());
    assertThat(dockManager.getContainers()).hasSize(1);
    myManager.initDockableContentFactory();

    myManager.openFileInNewWindow(file);
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
