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
      return Collections.EMPTY_SET;
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

  public void merge(final PomModelEvent event) {
    if(event.myChangeSets == null) return;
    if(myChangeSets == null){
      myChangeSets = new HashMap<PomModelAspect, PomChangeSet>(event.myChangeSets);
      return;
    }
    final Iterator<Map.Entry<PomModelAspect, PomChangeSet>> iterator = event.myChangeSets.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<PomModelAspect, PomChangeSet> entry = iterator.next();
      final PomModelAspect aspect = entry.getKey();
      final PomChangeSet pomChangeSet = myChangeSets.get(aspect);
      if(pomChangeSet != null)
        pomChangeSet.merge(entry.getValue());
      else myChangeSets.put(aspect, entry.getValue());
    }
  }
}