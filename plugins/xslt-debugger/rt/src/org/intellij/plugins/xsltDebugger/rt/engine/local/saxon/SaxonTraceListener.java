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

package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon;

import com.icl.saxon.Context;
import com.icl.saxon.Controller;
import com.icl.saxon.Mode;
import com.icl.saxon.NodeHandler;
import com.icl.saxon.om.NamePool;
import com.icl.saxon.om.Navigator;
import com.icl.saxon.om.NodeInfo;
import com.icl.saxon.output.Emitter;
import com.icl.saxon.output.GeneralOutputter;
import com.icl.saxon.style.StyleElement;
import com.icl.saxon.trace.TraceListener;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;
import org.intellij.plugins.xsltDebugger.rt.engine.local.OutputEventQueueImpl;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * A Simple trace listener that writes messages to System.err
 */

public class SaxonTraceListener implements TraceListener {
  private static final boolean TRACE = "true".equals(System.getProperty("xslt.debugger.trace", "false"));

  private String indent = "";
  private final LocalDebugger myDebugger;
  private final Controller myController;
  private boolean myIsInitialized;

  public SaxonTraceListener(LocalDebugger debugger, Controller controller) {
    myDebugger = debugger;
    myController = controller;
  }

  /**
   * Called at start
   */

  public void open() {
    myDebugger.getEventQueue().startDocument();
    if (TRACE) {
      trace("<trace>");
    }
  }

  private static void trace(String s) {
    if (TRACE) {
      System.err.println(s);
    }
  }

  /**
   * Called at end
   */

  public void close() {
    myDebugger.getEventQueue().endDocument();
//        myDebugger.stopped();
    if (TRACE) {
      trace("</trace>");
    }
  }


  /**
   * Called for all top level elements
   */
  public void toplevel(NodeInfo element) {
    if (!myIsInitialized) {
      myIsInitialized = true;

      final Properties properties = myController.getOutputProperties();
      final String method = properties.getProperty(OutputKeys.METHOD);
      if (method == null || "xml".equals(method) || "html".equals(method)) {
        try {
          final Emitter emitter = myController.getOutputter().getEmitter();
          final GeneralOutputter outputter = new TracingOutputter(emitter, myController.getNamePool());

          final Field fOutputter = Controller.class.getDeclaredField("currentOutputter");
          fOutputter.setAccessible(true);
          fOutputter.set(myController, outputter);
        } catch (Exception e1) {
          System.err.println("Failed to change output emitter");
          e1.printStackTrace();
        }
      }
    }

    if (TRACE) {
      StyleElement e = (StyleElement)element;
      trace("<Top-level element=\"" + e.getDisplayName() + "\" line=\"" + e.getLineNumber() +
            "\" file=\"" + e.getSystemId() + "\" precedence=\"" + e.getPrecedence() + "\"/>");
    }
  }

  /**
   * Called when a node of the source tree gets processed
   */
  public void enterSource(NodeHandler handler, Context context) {
    NodeInfo curr = context.getContextNodeInfo();
    final String path = Navigator.getPath(curr);
    if (TRACE) {
      trace(indent + "<Source node=\"" + path
            + "\" line=\"" + curr.getLineNumber()
            + "\" mode=\"" + getModeName(context) + "\">");
      indent += " ";
    }

    myDebugger.pushSource(new SaxonSourceFrame(myDebugger.getSourceFrame(), curr));
  }

  /**
   * Called after a node of the source tree got processed
   */
  public void leaveSource(NodeHandler handler, Context context) {
    if (TRACE) {
      indent = indent.substring(0, indent.length() - 1);
      trace(indent + "</Source><!-- " +
            Navigator.getPath(context.getContextNodeInfo()) + " -->");
    }

    myDebugger.popSource();
  }

  /**
   * Called when an element of the stylesheet gets processed
   */
  public void enter(NodeInfo element, Context context) {
    if (element.getNodeType() == NodeInfo.ELEMENT) {
      if (TRACE) {
        trace(indent + "<Instruction element=\"" + element.getDisplayName() + "\" line=\"" + element.getLineNumber() + "\">");
        indent += " ";
      }

      myDebugger.enter(new SaxonFrameImpl(myDebugger.getCurrentFrame(), context, (StyleElement)element));
    }
  }

  /**
   * Called after an element of the stylesheet got processed
   */
  public void leave(NodeInfo element, Context context) {
    if (element.getNodeType() == NodeInfo.ELEMENT) {
//            final int lineNumber = element.getLineNumber();
//            final String uri = element.getSystemId();

      myDebugger.leave();

      if (TRACE) {
        indent = indent.substring(0, indent.length() - 1);
        trace(indent + "</Instruction> <!-- " +
              element.getDisplayName() + " -->");
      }
    }
  }

  static String getModeName(Context context) {
    Mode mode = context.getMode();
    if (mode == null) return "#none";
    int nameCode = mode.getNameCode();
    if (nameCode == -1) {
      return "#default";
    } else {
      return context.getController().getNamePool().getDisplayName(nameCode);
    }
  }

  private final class TracingOutputter extends GeneralOutputter {
    private final NamePool myNamePool;
    private final OutputEventQueueImpl myEventQueue;

    public TracingOutputter(Emitter emitter, NamePool namePool) {
      super(namePool);
      this.emitter = emitter;
      myNamePool = namePool;
      myEventQueue = myDebugger.getEventQueue();
    }

    public void writeAttribute(int nameCode, String value, boolean noEscape) throws TransformerException {
      if (myEventQueue.isEnabled()) {
        final String localName = myNamePool.getLocalName(nameCode);
        final String prefix = myNamePool.getPrefix(nameCode);
        myEventQueue.attribute(prefix, localName, myNamePool.getURI(nameCode), value);
      }
      super.writeAttribute(nameCode, value, noEscape);
    }

    public void writeComment(String comment) throws TransformerException {
      myEventQueue.comment(comment);
      super.writeComment(comment);
    }

    public void writeContent(char[] chars, int start, int length) throws TransformerException {
      myEventQueue.characters(new String(chars, start, length));
      super.writeContent(chars, start, length);
    }

    public void writeContent(StringBuffer chars, int start, int len) throws TransformerException {
      myEventQueue.characters(chars.substring(start, start + len));
      super.writeContent(chars, start, len);
    }

    public void writeEndTag(int nameCode) throws TransformerException {
      myEventQueue.endElement();
      super.writeEndTag(nameCode);
    }

    public void writePI(String target, String data) throws TransformerException {
      myEventQueue.pi(target, data);
      super.writePI(target, data);
    }

    public void writeStartTag(int nameCode) throws TransformerException {
      if (myEventQueue.isEnabled()) {
        final String localName = myNamePool.getLocalName(nameCode);
        final String prefix = myNamePool.getPrefix(nameCode);
        myEventQueue.startElement(prefix, localName, myNamePool.getURI(nameCode));
      }
      super.writeStartTag(nameCode);
    }
  }
}