package com.intellij.testFramework;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.util.containers.hash.LinkedHashMap;
import junit.framework.Assert;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class EditorListenerTracker {
  private final Map<Class, List> before;
  private final boolean myDefaultProjectInitialized;

  public EditorListenerTracker() {
    EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
    before = multicaster.getListeners();
    myDefaultProjectInitialized = ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized();
  }

  public void checkListenersLeak() {
    // listeners may hang on default project
    if (myDefaultProjectInitialized != ((ProjectManagerImpl)ProjectManager.getInstance()).isDefaultProjectInitialized()) return;

    EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
    Map<Class, List> after = multicaster.getListeners();
    Map<Class, List> leaked = new LinkedHashMap<Class, List>();
    for (Map.Entry<Class, List> entry : after.entrySet()) {
      Class aClass = entry.getKey();
      List beforeList = before.get(aClass);
      List afterList = entry.getValue();
      if (beforeList != null) {
        afterList.removeAll(beforeList);
      }
      if (!afterList.isEmpty()) {
        leaked.put(aClass, afterList);
      }
    }

    for (Map.Entry<Class, List> entry : leaked.entrySet()) {
      Class aClass = entry.getKey();
      List list = entry.getValue();
      Assert.fail("Listeners leaked for " + aClass+":\n"+list);
    }
  }
}
