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

package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trace.InstructionInfo;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

public class Saxon9TraceListener implements TraceListener {
  private static final boolean TRACE = "true".equals(System.getProperty("xslt.debugger.trace", "false"));

  private LocalDebugger myDebugger;

  public Saxon9TraceListener() {
  }

  public void setDebugger(LocalDebugger debugger) {
    myDebugger = debugger;
  }

  private static void trace(String s) {
    if (TRACE) {
      System.err.println(s);
    }
  }

  public void open() {
    myDebugger.getEventQueue().startDocument();
    if (TRACE) {
      trace("<trace>");
    }
  }

  public void close() {
    myDebugger.getEventQueue().endDocument();

    if (TRACE) {
      trace("</trace>");
    }
  }

  public void enter(InstructionInfo instructionInfo, XPathContext xPathContext) {
    if (TRACE) {
      trace("<" + instructionInfo + ">");
    }
    if (instructionInfo instanceof StyleElement) {
      myDebugger.enter(new Saxon9StyleFrame(myDebugger.getCurrentFrame(), (StyleElement)instructionInfo, xPathContext));
    }
  }

  public void leave(InstructionInfo instructionInfo) {
    if (TRACE) {
      trace("</>");
    }
    if (instructionInfo instanceof StyleElement) {
      myDebugger.leave();
    }
  }

  public void startCurrentItem(Item item) {
    if (item instanceof NodeInfo) {
      if (TRACE) {
        trace("<" + ((NodeInfo)item).getDisplayName() + ">");
      }
      myDebugger.pushSource(new Saxon9SourceFrame(myDebugger.getSourceFrame(), (NodeInfo)item));
    }
  }

  public void endCurrentItem(Item item) {
    myDebugger.popSource();
  }
}