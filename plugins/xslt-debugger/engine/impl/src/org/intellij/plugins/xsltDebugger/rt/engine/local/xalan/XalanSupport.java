package org.intellij.plugins.xsltDebugger.rt.engine.local.xalan;

import org.apache.xalan.processor.TransformerFactoryImpl;
import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xml.dtm.DTM;
import org.apache.xml.utils.DefaultErrorHandler;
import org.intellij.plugins.xsltDebugger.rt.engine.DebuggerStoppedException;
import org.intellij.plugins.xsltDebugger.rt.engine.local.LocalDebugger;
import org.w3c.dom.Node;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.util.TooManyListenersException;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.01.2009
*/
public class XalanSupport {
  public static boolean init(Transformer transformer, LocalDebugger dbg) {
    if (transformer instanceof TransformerImpl) {
      try {
        System.out.println("XALAN: " +
                           Class.forName("org.apache.xalan.Version", true, transformer.getClass().getClassLoader()).getMethod("getVersion")
                             .invoke(null));
        final TransformerImpl tr = (TransformerImpl)transformer;

        tr.setErrorListener(new DefaultErrorHandler(false) {
          @Override
          public void fatalError(TransformerException exception) throws TransformerException {
            if (!(exception.getCause() instanceof DebuggerStoppedException)) {
              super.fatalError(exception);
            }
          }
        });

        try {
          tr.getTraceManager().addTraceListener(new XalanTraceListener(dbg, tr));
        } catch (TooManyListenersException e) {
          throw new AssertionError(e);
        }

        return true;
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  public static TransformerFactory createTransformerFactory() {
    final TransformerFactoryImpl factory = new TransformerFactoryImpl();
    prepareFactory(factory);
    return factory;
  }

  public static TransformerFactory prepareFactory(TransformerFactory factory) {
    try {
      factory.setAttribute("http://xml.apache.org/xalan/properties/source-location", Boolean.TRUE);
      factory.setAttribute("http://xml.apache.org/xalan/features/optimize", Boolean.FALSE);
    } catch (Exception e) {
      // ignore
    }
    return factory;
  }

  public static String getPath(DTM dtm, int node) {
    String pre;
    switch (dtm.getNodeType(node)) {
      case Node.DOCUMENT_NODE:
        return "/";
      case Node.ELEMENT_NODE:
        pre = getPath(dtm, dtm.getParent(node));
        return (pre.equals("/") ? "" : pre) +
               "/" + dtm.getNodeName(node) + "[" + getNumberSimple(dtm, node) + "]";
      case Node.ATTRIBUTE_NODE:
        return getPath(dtm, dtm.getParent(node)) + "/@" + dtm.getNodeNameX(node);
      case Node.TEXT_NODE:
        pre = getPath(dtm, dtm.getParent(node));
        return (pre.equals("/") ? "" : pre) +
               "/text()[" + getNumberSimple(dtm, node) + "]";
      case Node.COMMENT_NODE:
        pre = getPath(dtm, dtm.getParent(node));
        return (pre.equals("/") ? "" : pre) +
               "/comment()[" + getNumberSimple(dtm, node) + "]";
      case Node.PROCESSING_INSTRUCTION_NODE:
        pre = getPath(dtm, dtm.getParent(node));
        return (pre.equals("/") ? "" : pre) +
               "/processing-instruction()[" + getNumberSimple(dtm, node) + "]";
    }
    return "?";
  }

  private static int getNumberSimple(DTM dtm, int node) {
    final String localName = dtm.getLocalName(node);

    String uri = dtm.getNamespaceURI(node);
    if (uri == null) uri = "";

    final short type = dtm.getNodeType(node);

    int i = 1;
    int p = node;
    while ((p = dtm.getPreviousSibling(p)) != DTM.NULL) {
      if (dtm.getNodeType(p) == type) {
        switch (type) {
          case Node.TEXT_NODE:
          case Node.COMMENT_NODE:
          case Node.PROCESSING_INSTRUCTION_NODE:
            i++;
            break;
          default:
            if (localName.equals(dtm.getLocalName(p)) && uri.equals(dtm.getNamespaceURI(p))) {
              i++;
            }
        }
      }
    }
    return i;
  }
}