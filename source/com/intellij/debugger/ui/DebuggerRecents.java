package com.intellij.debugger.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;

import java.util.LinkedList;
import java.util.Map;

/**
 * @author Lex
 */
public class DebuggerRecents implements ProjectComponent {
  private Map<Object, LinkedList<TextWithImportsImpl>> myRecentExpressions = new HashMap<Object, LinkedList<TextWithImportsImpl>>();

  DebuggerRecents() {
  }

  public static DebuggerRecents getInstance(Project project) {
    return project.getComponent(DebuggerRecents.class);
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public LinkedList<TextWithImportsImpl> getRecents(Object id) {
    LinkedList<TextWithImportsImpl> result = myRecentExpressions.get(id);
    if(result == null){
      result = new LinkedList<TextWithImportsImpl>();
      myRecentExpressions.put(id, result);
    }
    return result;
  }

  public void addRecent(Object id, TextWithImportsImpl recent) {
    LinkedList<TextWithImportsImpl> recents = getRecents(id);
    if(recents.size() >= DebuggerExpressionComboBox.MAX_ROWS) {
      recents.removeLast();
    }
    recents.remove(recent);
    recents.addFirst(recent);
  }

  public String getComponentName() {
    return "DebuggerRecents";
  }
}
