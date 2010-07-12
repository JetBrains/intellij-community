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

import com.intellij.util.SpinAllocator;

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.kohsuke.rngom.digested.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 19.07.2007
*/
public class RecursionSaveWalker extends DPatternWalker {
  private THashSet<DRefPattern> myVisited;

  protected RecursionSaveWalker() {
  }

  @Override
  public Void onGrammar(DGrammarPattern p) {
    try {
      return super.onGrammar(p);
    } catch (NullPointerException e) {
      return null; // missing start pattern
    }
  }

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

  protected Void onUnary(DUnaryPattern p) {
    try {
      return super.onUnary(p);
    } catch (NullPointerException e) {
      return null; // empty element
    }
  }

  protected void doAccept(DPattern... p) {
    myVisited = ourAllocator.alloc();
    try {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < p.length; i++) {
        p[i].accept(this);
      }
    } finally {
      ourAllocator.dispose(myVisited);
    }
  }

  private static final SpinAllocator<THashSet<DRefPattern>> ourAllocator = new SpinAllocator<THashSet<DRefPattern>>(
          new SpinAllocator.ICreator<THashSet<DRefPattern>>() {
            @SuppressWarnings({ "unchecked" })
            public THashSet<DRefPattern> createInstance() {
              return new THashSet<DRefPattern>(64, TObjectHashingStrategy.IDENTITY) {
                public void clear() {
                  if (size() == 0) return;
                  super.clear();
                  final int c = capacity();
                  if (c > 64) {
                    super.compact();
                  }
                }
              };
            }
          },
          new SpinAllocator.IDisposer<THashSet<DRefPattern>>() {
            public void disposeInstance(THashSet<DRefPattern> instance) {
              instance.clear();
            }
          });
}