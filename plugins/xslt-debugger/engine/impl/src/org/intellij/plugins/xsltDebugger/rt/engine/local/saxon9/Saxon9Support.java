package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.Controller;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.instruct.Debugger;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.CharacterMapExpander;
import net.sf.saxon.serialize.Emitter;
import net.sf.saxon.trans.XPathException;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import java.util.Properties;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.01.2009
*/
public class Saxon9Support {
  public static boolean init(Transformer transformer, final LocalDebugger dbg) {
    if (transformer instanceof Controller) {
      System.out.println("SAXON 9");
      final Controller controller = (Controller)transformer;
      ((Saxon9TraceListener)controller.getConfiguration().getTraceListener()).setDebugger(dbg);
      controller.getConfiguration().setLineNumbering(true);
      controller.getConfiguration().setCompileWithTracing(true);
      controller.getConfiguration().setMultiThreading(false);
      controller.getConfiguration().setSerializerFactory(new SerializerFactory(controller.getConfiguration()) {
        @Override
        protected Receiver createXMLSerializer(Emitter emitter,
                                               Properties props,
                                               PipelineConfiguration pipe,
                                               CharacterMapExpander characterMapExpander,
                                               ProxyReceiver normalizer) throws XPathException {
          return super.createXMLSerializer(emitter, props, pipe, characterMapExpander, normalizer);
        }

        @Override
        protected Emitter newXMLEmitter() {
          return new TracingOutputter(dbg.getEventQueue(), super.newXMLEmitter());
        }
      });
      controller.getConfiguration().setDebugger(new Debugger() {
        public SlotManager makeSlotManager() {
          return new SlotManager() {
            @Override
            public int allocateSlotNumber(StructuredQName qName) {
              System.out.println("qName = " + qName);
              return super.allocateSlotNumber(qName);
            }
          };
        }
      });
      return true;
    }
    return false;
  }

  public static TransformerFactory createTransformerFactory() {
    final TransformerFactoryImpl factory = new TransformerFactoryImpl();
    factory.setAttribute(FeatureKeys.TRACE_LISTENER, new Saxon9TraceListener());
    try {
      factory.setAttribute(FeatureKeys.OPTIMIZATION_LEVEL, "0");
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }
    return factory;
  }
}