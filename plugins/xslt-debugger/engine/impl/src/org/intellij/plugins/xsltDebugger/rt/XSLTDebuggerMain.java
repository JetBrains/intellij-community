/*
 * Copyright 2002-2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt;

import org.intellij.plugins.xslt.run.rt.XSLTMain;
import org.intellij.plugins.xslt.run.rt.XSLTRunner;
import org.intellij.plugins.xsltDebugger.rt.engine.local.saxon.SaxonSupport;
import org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9.Saxon9Support;
import org.intellij.plugins.xsltDebugger.rt.engine.local.xalan.XalanSupport;
import org.intellij.plugins.xsltDebugger.rt.engine.remote.DebuggerServer;

import javax.xml.transform.*;
import java.rmi.RemoteException;


/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 23.11.2007
*/
public class XSLTDebuggerMain implements XSLTMain {

  public TransformerFactory createTransformerFactory() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    final String type = System.getProperty("xslt.transformer.type");
    if ("xalan".equalsIgnoreCase(type)) {
      return XalanSupport.createTransformerFactory();
    } else if ("saxon".equalsIgnoreCase(type)) {
      return SaxonSupport.createTransformerFactory();
    } else if ("saxon9".equalsIgnoreCase(type)) {
      return Saxon9Support.createTransformerFactory();
    } else if (type != null) {
      throw new UnsupportedOperationException("Unsupported Transformer type '" + type + "'");
    }
    return XalanSupport.prepareFactory(XSLTRunner.createTransformerFactoryStatic());
  }

  public void start(Transformer transformer, Source source, Result result) throws TransformerException {
    try {
      DebuggerServer.create(transformer, source, result, Integer.getInteger("xslt.debugger.port"));
    } catch (RemoteException e) {
      throw new TransformerException(e.getMessage(), e.getCause());
    }
  }
}