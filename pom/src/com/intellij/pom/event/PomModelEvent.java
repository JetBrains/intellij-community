package com.intellij.pom.event;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;

import java.util.*;

public class PomModelEvent extends EventObject {
  private Map<PomModelAspect, PomChangeSet> myChangeSets;

  public PomModelEvent(PomModel source) {
    super(source);
  }

  public Set<PomModelAspect> getChangedAspects(){
    if (myChangeSets != null) {
      return myChangeSets.keySet();
    } else {
      return null;
    }
  }

  public void registerChangeSet(PomModelAspect aspect, PomChangeSet set) {
    if (myChangeSets == null) {
      myChangeSets = new HashMap<PomModelAspect, PomChangeSet>();
    }
    myChangeSets.put(aspect, set);
  }

  public PomChangeSet getChangeSet(PomModelAspect aspect) {
    if (myChangeSets == null) return null;
    return myChangeSets.get(aspect);
  }
}