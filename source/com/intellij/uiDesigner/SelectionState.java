package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.uiDesigner.componentTree.ComponentPtr;
import com.intellij.uiDesigner.componentTree.ComponentSelectionListener;

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

  SelectionState(final GuiEditor editor) {
    if (editor == null) {
      throw new IllegalArgumentException("editor cannot be null");
    }
    mySelectionHistory = new Stack<ComponentPtr[]>();
    editor.addComponentSelectionListener(new MyComponentSelectionListener());
  }

  public boolean isInsideChange(){
    ApplicationManager.getApplication().isDispatchThread();
    return myInsideChange;
  }

  public void setInsideChange(final boolean insideChange){
    ApplicationManager.getApplication().isDispatchThread();
    myInsideChange = insideChange;
  }

  public Stack<ComponentPtr[]> getSelectionHistory() {
    return mySelectionHistory;
  }

  public static ComponentPtr[] getPtrs(final GuiEditor editor){
    final ArrayList<RadComponent> selection = FormEditingUtil.getAllSelectedComponents(editor);
    final ComponentPtr[] ptrs = new ComponentPtr[selection.size()];
    for(int i = selection.size() - 1; i >= 0; i--){
      ptrs[i] = new ComponentPtr(editor, selection.get(i));
    }
    return ptrs;
  }

  private final class MyComponentSelectionListener implements ComponentSelectionListener{

    public void selectedComponentChanged(final GuiEditor source) {
      if(myInsideChange){ // do not react on own events
        return;
      }
      mySelectionHistory.clear();
      mySelectionHistory.push(getPtrs(source));
    }
  }

}
