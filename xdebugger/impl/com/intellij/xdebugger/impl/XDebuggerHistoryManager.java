package com.intellij.xdebugger.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class XDebuggerHistoryManager {
  public static final int MAX_RECENT_EXPRESSIONS = 10;
  private Map<String, LinkedList<String>> myRecentExpressions = new HashMap<String, LinkedList<String>>();

  public static XDebuggerHistoryManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, XDebuggerHistoryManager.class);
  }

  public void addRecentExpression(@NotNull @NonNls String id, @NotNull String expression) {
    if (expression.trim().length() == 0) return;

    LinkedList<String> list = myRecentExpressions.get(id);
    if (list == null) {
      list = new LinkedList<String>();
      myRecentExpressions.put(id, list);
    }
    if (list.size() == MAX_RECENT_EXPRESSIONS) {
      list.removeLast();
    }
    list.remove(expression);
    list.addFirst(expression);
  }

  public List<String> getRecentExpressions(@NonNls String id) {
    LinkedList<String> list = myRecentExpressions.get(id);
    return list != null ? list : Collections.<String>emptyList();
  }
}
