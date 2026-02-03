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

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.InstructionDetails;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
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

  @Override
  public void open(Controller controller) {
    myDebugger.getEventQueue().startDocument();
    trace("<trace>");
  }

  @Override
  public void setOutputDestination(Logger logger) {

  }

  @Override
  public void close() {
    myDebugger.getEventQueue().endDocument();
    trace("</trace>");
  }

  @Override
  public void enter(InstructionInfo instructionInfo, XPathContext xPathContext) {
    if (MUTED) return;
    trace("<" + instructionInfo + ">");
    if (instructionInfo instanceof TraceExpression) {
      myDebugger.enter(new Saxon9StyleFrame(myDebugger.getCurrentFrame(), (TraceExpression)instructionInfo, xPathContext));
    } else if (instructionInfo instanceof InstructionDetails) {
      myDebugger.enter(new VirtualFrame(myDebugger.getCurrentFrame(), (InstructionDetails)instructionInfo));
    }
  }

  @Override
  public void leave(InstructionInfo instructionInfo) {
    if (MUTED) return;
    trace("</>");
    if (instructionInfo instanceof TraceExpression || instructionInfo instanceof InstructionDetails) {
      myDebugger.leave();
    }
  }

  @Override
  public void startCurrentItem(Item item) {
    if (MUTED) return;
    if (item instanceof NodeInfo) {
      trace("<" + ((NodeInfo)item).getDisplayName() + ">");
      myDebugger.pushSource(new Saxon9SourceFrame(myDebugger.getSourceFrame(), (NodeInfo)item));
    }
  }

  @Override
  public void endCurrentItem(Item item) {
    if (MUTED) return;
    if (item instanceof NodeInfo) {
      myDebugger.popSource();
    }
  }

  private static class VirtualFrame extends AbstractSaxon9Frame<Debugger.StyleFrame, SourceLocator> implements Debugger.StyleFrame {

    VirtualFrame(Debugger.StyleFrame previous, InstructionDetails instr) {
      super(previous, new MySource(instr));
    }

    @Override
    public String getInstruction() {
      return getPrevious().getInstruction();
    }

    @Override
    public Value eval(String expr) throws Debugger.EvaluationException {
      return getPrevious().eval(expr);
    }

    @Override
    public List<Debugger.Variable> getVariables() {
      return getPrevious().getVariables();
    }

    private static class MySource implements Source, SourceLocator {
      private final InstructionDetails myInstruction;

      MySource(InstructionDetails instr) {
        myInstruction = instr;
      }

      @Override
      public void setSystemId(String systemId) {
      }

      @Override
      public String getPublicId() {
        return null;
      }

      @Override
      public String getSystemId() {
        return myInstruction.getSystemId();
      }

      @Override
      public int getLineNumber() {
        return myInstruction.getLineNumber();
      }

      @Override
      public int getColumnNumber() {
        return myInstruction.getColumnNumber();
      }
    }
  }
}