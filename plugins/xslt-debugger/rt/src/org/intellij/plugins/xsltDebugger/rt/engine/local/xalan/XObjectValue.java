package org.intellij.plugins.xsltDebugger.rt.engine.local.xalan;

import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMIterator;
import org.apache.xml.dtm.ref.DTMNodeIterator;
import org.apache.xml.serializer.ToXMLStream;
import org.apache.xpath.objects.XNodeSet;
import org.apache.xpath.objects.XObject;
import org.apache.xpath.objects.XRTreeFrag;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import java.io.StringWriter;
import java.util.ArrayList;

class XObjectValue implements Value {
  private String myTypeString;
  private Object myValue;

  public XObjectValue(XObject value) {
    try {
      if (value != null) {
        myTypeString = value.getTypeString().replaceAll("#", "");
      } else {
        myTypeString = "undefined";
      }

      if (value instanceof XNodeSet) {
        final ArrayList<Node> nodes = new ArrayList<Node>();
        final DTMIterator v = value.mutableNodeset();
        for (int i = 0; i < v.getLength(); i++) {
          final int p = v.item(i);
          final DTM dtm = v.getDTM(p);
          if (dtm == null) continue;

          final SourceLocator loc = dtm.getSourceLocatorFor(p);
          nodes.add(new Node(loc != null ? loc.getSystemId() : null, loc != null ? loc.getLineNumber() : -1, XalanSupport.getPath(dtm, p),
                             dtm.getStringValue(p).toString()));
        }

        myValue = new NodeSet(value.str(), nodes);
      } else if (value instanceof XRTreeFrag) {
        final org.w3c.dom.Node node = ((DTMNodeIterator)value.object()).nextNode();
        if (node == null) {
          myValue = "";
        } else {
          try {
            final ToXMLStream stream = new ToXMLStream();
            final StringWriter writer = new StringWriter();
            stream.setWriter(writer);
            stream.setOmitXMLDeclaration(true);
            stream.serialize(node);

            myValue = writer.toString();
          } catch (Exception e) {
            e.printStackTrace();
            myValue = "???";
          }
        }
      } else if (value != null) {
        myValue = value.object();
      }
    } catch (TransformerException e) {
      myTypeString = "UNKNOWN";
    }
  }

  public Object getValue() {
    return myValue;
  }

  public Type getType() {
    try {
      return XPathType.valueOf(myTypeString.toUpperCase());
    } catch (IllegalArgumentException e) {
      return new ObjectType(myTypeString);
    }
  }
}