/*
 * Class ValueLookupManager
 * @author Jeka
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;

import java.awt.*;
import java.awt.event.MouseEvent;

public class ValueLookupManager implements EditorMouseMotionListener, EditorMouseListener, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueLookupManager");

  private Project myProject;
  private Alarm myAlarm = new Alarm();
  private ValueHint myRequest = null;

  public ValueLookupManager(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(this);
    EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(this);
  }

  public void projectClosed() {
    EditorFactory.getInstance().getEventMulticaster().removeEditorMouseMotionListener(this);
    EditorFactory.getInstance().getEventMulticaster().removeEditorMouseListener(this);
    myAlarm.cancelAllRequests();
  }

  static boolean isAltMask(int modifiers) {
    return modifiers == java.awt.event.InputEvent.ALT_MASK;
  }

  public void mouseDragged(EditorMouseEvent e) {
  }

  public void mouseMoved(EditorMouseEvent e) {
    if (e.isConsumed()) {
      return;
    }
    Editor editor = e.getEditor();
    Point point = e.getMouseEvent().getPoint();
    requestHint(editor, point, isAltMask(e.getMouseEvent().getModifiers()) ? ValueHint.MOUSE_ALT_OVER_HINT : ValueHint.MOUSE_OVER_HINT);
  }

  public void requestHint(final Editor editor, final Point point, final int type) {
    if (myRequest != null) {
      if(myRequest.isKeepHint(editor, point)) return;
      hideHint();
    }

    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).getContext().getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isPaused()) return;

    myAlarm.cancelAllRequests();
    if(type == ValueHint.MOUSE_OVER_HINT) {
      myAlarm.addRequest(new Runnable() {
        public void run() {
          showHint(editor, point, type);
        }
      }, DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY);
    } else {
      showHint(editor, point, type);
    }

  }

  public void hideHint() {
    if(myRequest != null) {
      myRequest.hideHint();
      myRequest = null;
    }
  }
  public void showHint(Editor editor, Point point, int type) {
    myAlarm.cancelAllRequests();
    hideHint();
    if(editor.isDisposed()) return;
    DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).getContext().getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isAttached()) return;
    myRequest = new ValueHint(myProject, editor, point, type);
    myRequest.invokeHint();
  }

  public void mouseReleased(EditorMouseEvent e) {
    //To change body of implemented methods use Options | File Templates.
  }

  public void mouseClicked(EditorMouseEvent e) {
    if(isAltMask(e.getMouseEvent().getModifiers() & ~(java.awt.event.InputEvent.BUTTON1_MASK)) && (e.getMouseEvent().getButton() == MouseEvent.BUTTON1)){
      showHint(e.getEditor(), e.getMouseEvent().getPoint(), ValueHint.MOUSE_CLICK_HINT);
    }
  }

  public void mouseExited(EditorMouseEvent e) {
    //To change body of implemented methods use Options | File Templates.
  }

  public void mouseEntered(EditorMouseEvent e) {
    //To change body of implemented methods use Options | File Templates.
  }

  public void mousePressed(EditorMouseEvent e) {
    //To change body of implemented methods use Options | File Templates.
  }


  public static ValueLookupManager getInstance(Project project) {
    return project.getComponent(ValueLookupManager.class);
  }

  public String getComponentName() {
    return "ValueLookupManager";
  }
}