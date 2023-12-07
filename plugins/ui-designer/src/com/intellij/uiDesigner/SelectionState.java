// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * This class implements Ctrl+W / Ctrl+Shift+W functionality in the GuiEditor
 */
public final class SelectionState{
  private final Stack<ComponentPtr[]> mySelectionHistory;
  /** We do not need to handle our own events */
  private boolean myInsideChange;

  public SelectionState(final @NotNull GuiEditor editor) {
    mySelectionHistory = new Stack<>();
    editor.addComponentSelectionListener(new MyComponentSelectionListener());
  }

  public void setInsideChange(final boolean insideChange){
    ThreadingAssertions.assertEventDispatchThread();
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

    @Override
    public void selectedComponentChanged(final @NotNull GuiEditor source) {
      if(myInsideChange){ // do not react on own events
        return;
      }
      mySelectionHistory.clear();
      mySelectionHistory.push(getSelection(source));
    }
  }

}
