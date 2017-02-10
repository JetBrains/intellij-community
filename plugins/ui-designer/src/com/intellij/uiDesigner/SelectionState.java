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
package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Stack;

/**
 * This class implements Ctrl+W / Ctrl+Shift+W functionality in the GuiEditor
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SelectionState{
  private final Stack<ComponentPtr[]> mySelectionHistory;
  /** We do not need to handle our own events */
  private boolean myInsideChange;

  public SelectionState(@NotNull final GuiEditor editor) {
    mySelectionHistory = new Stack<>();
    editor.addComponentSelectionListener(new MyComponentSelectionListener());
  }

  public void setInsideChange(final boolean insideChange){
    ApplicationManager.getApplication().assertIsDispatchThread();
    myInsideChange = insideChange;
  }

  public Stack<ComponentPtr[]> getSelectionHistory() {
    return mySelectionHistory;
  }

  public static ComponentPtr[] getSelection(final GuiEditor editor){
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(editor);
    final ComponentPtr[] ptrs = new ComponentPtr[selection.size()];
    for(int i = selection.size() - 1; i >= 0; i--){
      ptrs[i] = new ComponentPtr(editor, selection.get(i));
    }
    return ptrs;
  }

  public static void restoreSelection(final GuiEditor editor, final ComponentPtr[] ptrs) {
    FormEditingUtil.clearSelection(editor.getRootContainer());
    for(int i = ptrs.length - 1; i >= 0; i--){
      final ComponentPtr ptr = ptrs[i];
      ptr.validate();
      if(ptr.isValid()){
        ptr.getComponent().setSelected(true);
      }
    }
  }

  private final class MyComponentSelectionListener implements ComponentSelectionListener{

    public void selectedComponentChanged(final GuiEditor source) {
      if(myInsideChange){ // do not react on own events
        return;
      }
      mySelectionHistory.clear();
      mySelectionHistory.push(getSelection(source));
    }
  }

}
