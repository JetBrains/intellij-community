package org.intellij.plugins.xsltDebugger.rt.engine.local.xalan;

import org.apache.xalan.trace.GenerateEvent;
import org.apache.xalan.trace.PrintTraceListener;
import org.apache.xalan.trace.TracerEvent;
import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMIterator;
import org.apache.xml.serializer.SerializationHandler;
import org.apache.xml.serializer.SerializerTrace;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.local.AbstractFrame;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

import javax.xml.transform.SourceLocator;
import java.io.PrintWriter;

public class XalanTraceListener extends PrintTraceListener {
  private final LocalDebugger myDebugger;
  private final TransformerImpl myTransformer;

  private volatile boolean myTracing = false;
  private boolean firstTrace = true;

  public XalanTraceListener(LocalDebugger localDebugger, TransformerImpl tr) {
    super(new PrintWriter(System.out, true));
    m_traceElements = false;
    m_traceGeneration = false;
    m_traceSelection = false;

    myDebugger = localDebugger;
    myTransformer = tr;
  }

  @Override
  public void trace(TracerEvent ev) {
    if (myTracing) return;
    myTracing = true; // prevents handling of recursive trace() events

    try {
      // init
      if (firstTrace) {
        firstTrace = false;
        final SerializationHandler handler = myTransformer.getSerializationHandler();
        myTransformer.setSerializationHandler(new TracingSerializationHandler(myDebugger, handler));
      }

      super.trace(ev);

      final DTMIterator iterator = myTransformer.getContextNodeList();
      final int node = myTransformer.getMatchedNode();
      final Debugger.SourceFrame sourceFrame = myDebugger.getSourceFrame();
      final boolean withSource;
      if (sourceFrame == null || ((MySourceFrame)sourceFrame).getMatchedNode() != node) {
        myDebugger.pushSource(new MySourceFrame(sourceFrame, iterator.getDTM(node), node));
        withSource = true;
      } else {
        withSource = false;
      }
      myDebugger.enter(new XalanStyleFrame(ev, myDebugger.getCurrentFrame(), withSource));
    } finally {
      myTracing = false;
    }
  }

  @Override
  public void traceEnd(TracerEvent ev) {
    if (myTracing) return;

    if (myDebugger.getCurrentFrame() == null) {
      return;
    }

    // xsl:choose (and maybe others) don't generate traceEnd()-events
    final String instr = XalanStyleFrame.getInstruction(ev.m_styleNode);
    if (instr != null) {
      while (!instr.equals(myDebugger.getCurrentFrame().getInstruction())) {
        leave();
      }
    }

    super.traceEnd(ev);
    leave();
  }

  private void leave() {
    if (((XalanStyleFrame)myDebugger.getCurrentFrame()).isWithSourceFrame()) {
      myDebugger.popSource();
    }
    myDebugger.leave();
  }

  @Override
  public void generated(GenerateEvent ev) {
    if (!(myTransformer.getSerializationHandler() instanceof TracingSerializationHandler)) {
      // internal RTF evaluation, don't care
      return;
    }
    switch (ev.m_eventtype) {
      case SerializerTrace.EVENTTYPE_STARTDOCUMENT:
        myDebugger.getEventQueue().startDocument();
        break;
      case SerializerTrace.EVENTTYPE_ENDDOCUMENT:
        myDebugger.getEventQueue().endDocument();
        break;

      case SerializerTrace.EVENTTYPE_ENDELEMENT:
        myDebugger.getEventQueue().endElement();
        break;

      case SerializerTrace.EVENTTYPE_CDATA:
      case SerializerTrace.EVENTTYPE_CHARACTERS:
        myDebugger.getEventQueue().characters(new String(ev.m_characters, ev.m_start, ev.m_length));
        break;
      case SerializerTrace.EVENTTYPE_COMMENT:
        myDebugger.getEventQueue().comment(ev.m_data);
        break;
      case SerializerTrace.EVENTTYPE_PI:
        myDebugger.getEventQueue().pi(ev.m_name, ev.m_data);
        break;
    }
  }

  private static class MySourceFrame extends AbstractFrame<Debugger.SourceFrame> implements Debugger.SourceFrame {
    private final String mySystemId;
    private final int myLineNumber;
    private final int myMatchedNode;
    private final String myPath;

    public MySourceFrame(Debugger.SourceFrame sourceFrame, DTM dtm, int node) {
      super(sourceFrame);

      final SourceLocator loc = dtm.getSourceLocatorFor(node);
      mySystemId = loc.getSystemId();
      myLineNumber = loc.getLineNumber();

      myPath = XalanSupport.getPath(dtm, node);
      myMatchedNode = node;
    }

    public String getXPath() {
      return myPath;
    }

    public String getURI() {
      return mySystemId;
    }

    public int getLineNumber() {
      return myLineNumber;
    }

    public int getMatchedNode() {
      return myMatchedNode;
    }
  }
}