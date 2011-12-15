/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.local;

import org.intellij.plugins.xsltDebugger.rt.engine.Debugger.Frame;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 02.06.2007
 */
public abstract class AbstractFrame<F extends Frame> implements Frame<F> {
  private final F myPrev;
  private F myNext;

  private boolean myValid = true;

  public AbstractFrame(F prev) {
    myPrev = prev;

    if (prev != null) {
      ((AbstractFrame)prev).myNext = this;
    }
  }

  public void invalidate() {
    assert myValid;
    assert myNext == null;
    if (myPrev != null) {
      ((AbstractFrame)myPrev).myNext = null;
    }
    myValid = false;
  }

  public F getNext() {
    assert myValid;
    return myNext;
  }

  public F getPrevious() {
    assert myValid;
    return myPrev;
  }

  public boolean isValid() {
    return myValid;
  }

  protected static void debug(Throwable e) {
    assert _debug(e);
  }

  @SuppressWarnings("CallToPrintStackTrace")
  private static boolean _debug(Throwable e) {
    e.printStackTrace();
    return true;
  }
}
