/*
 * Class ValueLookupManager
 * @author Jeka
 */
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ValueLookupManager implements EditorMouseMotionListener, ProjectComponent {
  private Project myProject;
  private Alarm myAlarm = new Alarm();
  private AbstractValueHint myRequest = null;

  public ValueLookupManager(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(this);
  }

  public void projectClosed() {
    EditorFactory.getInstance().getEventMulticaster().removeEditorMouseMotionListener(this);
    myAlarm.cancelAllRequests();
  }

  public void mouseDragged(EditorMouseEvent e) {
  }

  public void mouseMoved(EditorMouseEvent e) {
    if (e.isConsumed()) {
      return;
    }

    Editor editor = e.getEditor();
    Point point = e.getMouseEvent().getPoint();
    if (myRequest != null) {
      if(myRequest.isKeepHint(editor, point)) return;
      hideHint();
    }

    DebuggerSupport[] supports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : supports) {
      QuickEvaluateHandler handler = support.getQuickEvaluateHandler();
      if (handler.isEnabled(myProject)) {
        requestHint(handler, editor, point, AbstractValueHint.getType(e));
        break;
      }
    }
  }

  private void requestHint(final QuickEvaluateHandler handler, final Editor editor, final Point point, final int type) {
    myAlarm.cancelAllRequests();
    if(type == AbstractValueHint.MOUSE_OVER_HINT) {
      myAlarm.addRequest(new Runnable() {
        public void run() {
          showHint(handler, editor, point, type);
        }
      }, handler.getValueLookupDelay());
    } else {
      showHint(handler, editor, point, type);
    }

  }

  public void hideHint() {
    if(myRequest != null) {
      myRequest.hideHint();
      myRequest = null;
    }
  }

  public void showHint(final QuickEvaluateHandler handler, Editor editor, Point point, int type) {
    myAlarm.cancelAllRequests();
    hideHint();
    if (editor.isDisposed() || !handler.canShowHint(myProject)) return;

    myRequest = handler.createValueHint(myProject, editor, point, type);
    if (myRequest != null) {
      myRequest.invokeHint();
    }
  }

  public static ValueLookupManager getInstance(Project project) {
    return project.getComponent(ValueLookupManager.class);
  }

  @NotNull
  public String getComponentName() {
    return "ValueLookupManager";
  }
}