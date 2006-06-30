/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      return Collections.emptySet();
    }
  }

  public void registerChangeSet(PomModelAspect aspect, PomChangeSet set) {
    if (myChangeSets == null) {
      myChangeSets = new HashMap<PomModelAspect, PomChangeSet>();
    }
    if (set != null) {
      myChangeSets.put(aspect, set);
    }
    else {
      myChangeSets.remove(aspect);
    }
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
    for (final Map.Entry<PomModelAspect, PomChangeSet> entry : event.myChangeSets.entrySet()) {
      final PomModelAspect aspect = entry.getKey();
      final PomChangeSet pomChangeSet = myChangeSets.get(aspect);
      if (pomChangeSet != null) {
        pomChangeSet.merge(entry.getValue());
      }
      else {
        myChangeSets.put(aspect, (PomChangeSet)entry.getValue());
      }
    }
  }
}