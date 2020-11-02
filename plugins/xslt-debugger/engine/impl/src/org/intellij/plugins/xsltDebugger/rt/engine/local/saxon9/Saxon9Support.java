// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.Controller;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.expr.instruct.Debugger;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.jaxp.TransformerImpl;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.serialize.Emitter;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import java.util.Properties;

public final class Saxon9Support {
  public static boolean init(Transformer transformer, final LocalDebugger dbg) {
    Controller controller = ((TransformerImpl)transformer).getUnderlyingController();
    System.out.println("SAXON 9");
    ((Saxon9TraceListener)controller.getConfiguration().getTraceListener()).setDebugger(dbg);
    controller.getConfiguration().setLineNumbering(true);
    controller.getConfiguration().setCompileWithTracing(true);

    controller.getConfiguration().setSerializerFactory(new SerializerFactory(controller.getConfiguration()) {

      @Override
      protected Emitter newXMLEmitter(Properties properties) {
        return new TracingOutputter(dbg.getEventQueue(), super.newXMLEmitter(properties));
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