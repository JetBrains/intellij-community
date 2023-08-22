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

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.kohsuke.rngom.digested.*;

import java.util.Set;

public class RecursionSaveWalker extends DPatternWalker {
  private final Set<DPattern> myVisited = new ReferenceOpenHashSet<>(256);

  protected RecursionSaveWalker() {
  }

  @Override
  public Void onGrammar(DGrammarPattern p) {
    if (myVisited.add(p)) {
      try {
        return super.onGrammar(p);
      } catch (NullPointerException e) {
        return null; // missing start pattern
      }
    }
    return null;
  }

  @Override
  public Void onRef(DRefPattern p) {
    if (myVisited.add(p)) {
      try {
        return super.onRef(p);
      } catch (NullPointerException e) {
        return null; // unresolved ref
      }
    }
    return null;
  }

  @Override
  protected Void onUnary(DUnaryPattern p) {
    if (myVisited.add(p)) {
      try {
        return super.onUnary(p);
      } catch (NullPointerException e) {
        return null; // empty element
      }
    }
    return null;
  }

  protected void doAccept(DPattern... p) {
    myVisited.clear();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < p.length; i++) {
      p[i].accept(this);
    }
    myVisited.clear();
  }
}