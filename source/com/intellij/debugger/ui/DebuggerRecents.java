package com.intellij.debugger.ui;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;

import java.util.LinkedList;
import java.util.Map;

/**
 * @author Lex
 */
public class DebuggerRecents implements ProjectComponent {
  private Map<Object, LinkedList<TextWithImports>> myRecentExpressions = new HashMap<Object, LinkedList<TextWithImports>>();

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

  public LinkedList<TextWithImports> getRecents(Object id) {
    LinkedList<TextWithImports> result = myRecentExpressions.get(id);
    if(result == null){
      result = new LinkedList<TextWithImports>();
      myRecentExpressions.put(id, result);
    }
    return result;
  }

  public void addRecent(Object id, TextWithImports recent) {
    LinkedList<TextWithImports> recents = getRecents(id);
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
