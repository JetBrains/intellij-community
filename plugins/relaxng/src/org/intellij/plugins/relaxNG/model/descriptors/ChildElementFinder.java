/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.model.descriptors;

import org.kohsuke.rngom.digested.DAttributePattern;
import org.kohsuke.rngom.digested.DElementPattern;
import org.kohsuke.rngom.digested.DPattern;
import org.kohsuke.rngom.digested.DRefPattern;

import java.util.ArrayList;
import java.util.List;

class ChildElementFinder extends RecursionSaveWalker {
  private final List<DElementPattern> myRoots = new ArrayList<DElementPattern>();

  private final int myTargetDepth;
  private int myDepth;

  private ChildElementFinder(int targetDepth) {
    myTargetDepth = targetDepth;
  }

  public Void onRef(DRefPattern p) {
    if (myDepth < myTargetDepth) {
      return super.onRef(p);
    }
    return null;
  }

  public Void onElement(DElementPattern p) {
    myDepth++;
    try {
      if (myDepth == myTargetDepth) {
        myRoots.add(p);
        return null;
      } else {
        return super.onElement(p);
      }
    } finally {
      myDepth--;
    }
  }

  @Override
  public Void onAttribute(DAttributePattern p) {
    return null;
  }

  static List<DElementPattern> find(int targetDepth, DPattern p) {
    final ChildElementFinder finder = new ChildElementFinder(targetDepth);
    finder.doAccept(p);
    return finder.myRoots;
  }

  static List<DElementPattern> find(DPattern p) {
    return find(1, p);
  }
}