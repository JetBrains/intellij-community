package com.intellij.pom.tree;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;

import java.util.Collections;
import java.util.Set;

public class TreeAspect implements PomModelAspect{
  private final PomModel myModel;

  public TreeAspect(PomModel model) {
    myModel = model;
    myModel.registerAspect(TreeAspect.class, this, (Set<PomModelAspect>)Collections.EMPTY_SET);
  }

  public void projectOpened() {}
  public void projectClosed() {}
  public void initComponent() {}
  public void disposeComponent() {}
  public void update(PomModelEvent event) {}

  public String getComponentName() {
    return "Tree POM aspect";
  }
}
