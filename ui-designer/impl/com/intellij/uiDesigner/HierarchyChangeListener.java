package com.intellij.uiDesigner;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface HierarchyChangeListener extends EventListener{
  /**
   * This event is fired each time when the something was changes inside component tree.
   * For example root container changes, or some undoable action has beed performed, etc.
   */
  void hierarchyChanged();
}
