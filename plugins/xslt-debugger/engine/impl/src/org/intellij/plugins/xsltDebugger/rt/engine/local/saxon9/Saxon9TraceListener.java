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
import net.sf.saxon.expr.instruct.InstructionDetails;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trace.InstructionInfo;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import java.util.List;

public class Saxon9TraceListener implements TraceListener {
  private static final boolean TRACE = "true".equals(System.getProperty("xslt.debugger.trace", "false"));

  private LocalDebugger myDebugger;

  static boolean MUTED;

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
    if (MUTED) return;
    if (TRACE) {
      trace("<" + instructionInfo + ">");
    }
    if (instructionInfo instanceof StyleElement) {
      myDebugger.enter(new Saxon9StyleFrame(myDebugger.getCurrentFrame(), (StyleElement)instructionInfo, xPathContext));
    } else if (instructionInfo instanceof InstructionDetails) {
      myDebugger.enter(new VirtualFrame(myDebugger.getCurrentFrame(), (InstructionDetails)instructionInfo));
    }
  }

  public void leave(InstructionInfo instructionInfo) {
    if (MUTED) return;
    if (TRACE) {
      trace("</>");
    }
    if (instructionInfo instanceof StyleElement || instructionInfo instanceof InstructionDetails) {
      myDebugger.leave();
    }
  }

  public void startCurrentItem(Item item) {
    if (MUTED) return;
    if (item instanceof NodeInfo) {
      if (TRACE) {
        trace("<" + ((NodeInfo)item).getDisplayName() + ">");
      }
      myDebugger.pushSource(new Saxon9SourceFrame(myDebugger.getSourceFrame(), (NodeInfo)item));
    }
  }

  public void endCurrentItem(Item item) {
    if (MUTED) return;
    if (item instanceof NodeInfo) {
      myDebugger.popSource();
    }
  }

  private static class VirtualFrame extends AbstractSaxon9Frame<Debugger.StyleFrame, Source> implements Debugger.StyleFrame {

    public VirtualFrame(Debugger.StyleFrame previous, InstructionDetails instr) {
      super(previous, new MySource(instr));
    }

    public String getInstruction() {
      return getPrevious().getInstruction();
    }

    public Value eval(String expr) throws Debugger.EvaluationException {
      return getPrevious().eval(expr);
    }

    public List<Debugger.Variable> getVariables() {
      return getPrevious().getVariables();
    }

    private static class MySource implements Source, SourceLocator {
      private final InstructionDetails myInstruction;

      public MySource(InstructionDetails instr) {
        myInstruction = instr;
      }

      public void setSystemId(String systemId) {
      }

      public String getPublicId() {
        return null;
      }

      public String getSystemId() {
        return myInstruction.getSystemId();
      }

      public int getLineNumber() {
        return myInstruction.getLineNumber();
      }

      public int getColumnNumber() {
        return myInstruction.getColumnNumber();
      }
    }
  }
}