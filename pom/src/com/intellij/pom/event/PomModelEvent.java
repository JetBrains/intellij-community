package com.intellij.pom.event;

import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PomModelEvent extends EventObject {
  private Map<PomModelAspect, PomChangeSet> myChangeSets;

  public PomModelEvent(PomModel source) {
    super(source);
  }

  public Set<PomModelAspect> getChangedAspects(){
    return myChangeSets.keySet();
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