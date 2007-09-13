/**
 * @author Vladimir Kondratyev
 */
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Requests focus for the editor component.
 */
public final class RequestFocusInEditorComponentCmd extends FinalizableCommand{
  private final FileEditorManagerEx myEditorManager;
  private final JComponent myComponent;
  private boolean myForced;
  private ActionCallback myDoneCallback;

  public RequestFocusInEditorComponentCmd(@NotNull final FileEditorManagerEx editorManager, final Runnable finishCallBack, boolean forced){
    super(finishCallBack);
    myEditorManager = editorManager;
    myComponent = myEditorManager.getPreferredFocusedComponent();
    myForced = forced;

    myDoneCallback = new ActionCallback();
  }

  public ActionCallback getDoneCallback() {
    return myDoneCallback;
  }

  public final void run(){
    try{
      final Window owner=SwingUtilities.getWindowAncestor(myEditorManager.getComponent());
      if(owner==null){
        return;
      }
      // if owner is active window or it has active child window which isn't floating decorator then
      // don't bring owner window to font. If we will make toFront every time then it's possible
      // the following situation:
      // 1. user prform refactoring
      // 2. "Do not show preview" dialog is popping up.
      // 3. At that time "preview" tool window is being activated and modal "don't show..." dialog
      // isn't active.
      if(!owner.isActive()){
        final Window activeWindow=getActiveWindow(owner.getOwnedWindows());
        if(activeWindow == null || (activeWindow instanceof FloatingDecorator)){
          //Thread.dumpStack();
          //System.out.println("------------------------------------------------------");
          owner.toFront();
        }
      }

      if(myComponent != null){
        myManager.requestFocus(myComponent, myForced).markDone(myDoneCallback);
      }

    }finally{
      finish();
    }
  }

  /**
   * @return first active window from hierarchy with specified roots. Returns <code>null</code>
   * if there is no active window in the hierarchy.
   */
  private Window getActiveWindow(final Window[] windows){
    for(int i=0;i<windows.length;i++){
      Window window=windows[i];
      if(window.isShowing()&&window.isActive()){
        return window;
      }
      window=getActiveWindow(window.getOwnedWindows());
      if(window!=null){
        return window;
      }
    }
    return null;
  }
}
