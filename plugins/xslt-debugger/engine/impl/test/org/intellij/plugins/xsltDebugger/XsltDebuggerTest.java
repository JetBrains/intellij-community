// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.xsltDebugger;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.util.containers.MultiMap;
import junit.framework.TestCase;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;
import org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9.Saxon9Support;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

public class XsltDebuggerTest extends TestCase {

  public void testDebugger() throws TransformerException {
    Transformer transformer = Saxon9Support.createTransformerFactory().newTransformer(getTestSource("test.xsl"));
    StreamSource source = new StreamSource(new StringReader("<root/>"));
    StreamResult result = new StreamResult(new StringWriter());
    final MultiMap<Debugger.StyleFrame, Debugger.Variable> frames = MultiMap.createLinked();
    LocalDebugger debugger = new LocalDebugger(transformer, source, result) {

      @Override
      public void enter(StyleFrame frame) {
        super.enter(frame);
        frames.put(frame, frame.getVariables());
      }
    };
    Saxon9Support.init(transformer, debugger);
    transformer.transform(source, result);

    assertEquals(6, frames.size());
    assertEquals("trace", frames.keySet().iterator().next().getInstruction());
  }

  private static StreamSource getTestSource(String file) {
    String path = PathManagerEx.getCommunityHomePath() + "/plugins/xslt-debugger/engine/impl/testData/" + file;
    return new StreamSource(new File(path));
  }
}
