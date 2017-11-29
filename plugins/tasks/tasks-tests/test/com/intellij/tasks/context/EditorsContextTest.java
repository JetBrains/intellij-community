/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.tasks.context;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.ui.docking.DockManager;
import org.jdom.Element;

/**
 * @author Dmitry Avdeev
 */
@SkipInHeadlessEnvironment
public class EditorsContextTest extends FileEditorManagerTestCase {

  public void testDockableContainer() {

    VirtualFile file = getFile("/foo.txt");
    myManager.openFile(file, false);
    DockManager dockManager = DockManager.getInstance(getProject());
    assertEquals(1, dockManager.getContainers().size());
    myManager.initDockableContentFactory();

    myManager.openFileInNewWindow(file);
    assertEquals(2, dockManager.getContainers().size());

    Element context = new Element("context");
    WorkingContextManager contextManager = WorkingContextManager.getInstance(getProject());
    contextManager.saveContext(context);
    assertEquals(2, context.getChild("editors").getChildren().size());
    assertEquals(2, EditorFactory.getInstance().getAllEditors().length);

    contextManager.clearContext();
    assertEquals(1, dockManager.getContainers().size());
    assertEquals(0, EditorFactory.getInstance().getAllEditors().length);

    //contextManager.loadContext(context);
    //assertEquals(2, dockManager.getContainers().size());
    //Editor[] editors = EditorFactory.getInstance().getAllEditors();
    //assertEquals(2, editors.length);
    //
    //contextManager.clearContext();
  }

  protected String getBasePath() {
    return "/plugins/tasks/tasks-tests/testData/context";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

}
