package com.intellij.pom.tree;

import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.events.TreeChangeEvent;

public class TreeAspectEvent extends PomModelEvent {
  public TreeAspectEvent(PomModel model, TreeChangeEvent reparseAccumulatedEvent) {
    super(model);
    registerChangeSet(model.getModelAspect(TreeAspect.class), reparseAccumulatedEvent);
  }
}
