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

import com.icl.saxon.Bindery;
import com.icl.saxon.Binding;
import com.icl.saxon.Context;
import com.icl.saxon.expr.*;
import com.icl.saxon.om.*;
import com.icl.saxon.output.GeneralOutputter;
import com.icl.saxon.style.ExpressionContext;
import com.icl.saxon.style.StyleElement;
import com.icl.saxon.style.XSLGeneralVariable;
import com.icl.saxon.style.XSLParam;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableComparator;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableImpl;
import org.w3c.dom.Node;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 23.05.2007
 */
class SaxonFrameImpl extends AbstractSaxonFrame<Debugger.StyleFrame, StyleElement> implements Debugger.StyleFrame {

  private static Field fGeneralUseAllowed;

  static {
    try {
      fGeneralUseAllowed = SingletonNodeSet.class.getDeclaredField("generalUseAllowed");
      fGeneralUseAllowed.setAccessible(true);
    } catch (NoSuchFieldException e) {
      fGeneralUseAllowed = null;
      System.err.println("Failed to get field com.icl.saxon.expr.SingletonNodeSet.generalUseAllowed - incompatible Saxon version?");
      e.printStackTrace();
    }
  }

  private final Context myContext;
  private final int myFrameId;

  SaxonFrameImpl(Debugger.StyleFrame prev, Context context, StyleElement element) {
    super(prev, element);
    myContext = context;
    myFrameId = context.getBindery().getFrameId();
  }

  public String getInstruction() {
    return myElement.getDisplayName();
  }

  public List<Debugger.Variable> getVariables() {
    assert isValid();

    final ArrayList<Debugger.Variable> variables = new ArrayList<Debugger.Variable>();
    final Enumeration[] variableNames = myElement.getVariableNames();

    this.addVariables(myElement, variables, variableNames[0], true);
    this.addVariables(myElement, variables, variableNames[1], false);

    Collections.sort(variables, VariableComparator.INSTANCE);

    return variables;
  }

  public Value eval(String expr) throws Debugger.EvaluationException {
    assert isValid();

    try {
      // trick to avoid exception when evaluating variable references on xsl:template frames
      final MyDummyElement dummy = new MyDummyElement(myElement);
      final Expression expression = Expression.make(expr, dummy.getStaticContext());
      return convertValue(expression.evaluate(myContext));
    } catch (XPathException e) {
      throw new Debugger.EvaluationException(e.getMessage());
    }
  }

  private Value convertValue(com.icl.saxon.expr.Value v) throws XPathException {
    return MyValue.create(v, myContext);
  }

  void addVariables(StyleElement element, ArrayList<Debugger.Variable> variables, Enumeration enumeration, boolean isGlobal) {
    final Context context = myContext;
    final StaticContext ctx = context.getStaticContext();

    final NamePool pool = element.getNamePool();
    final Bindery bindery = context.getBindery();

    while (enumeration.hasMoreElements()) {
      String name = (String)enumeration.nextElement();
      try {
        final String[] parts = name.split("\\^");
        final String realname = parts[1];
        final int fingerprint = ctx != null ? ctx.getFingerprint(realname, false) : pool.getFingerprint(parts[0], realname);
        final Binding binding = element.getVariableBinding(fingerprint);
        final Debugger.Variable.Kind kind =
          binding instanceof XSLParam ? Debugger.Variable.Kind.PARAMETER : Debugger.Variable.Kind.VARIABLE;
        final com.icl.saxon.expr.Value value = bindery.getValue(binding, myFrameId);

        if (binding instanceof XSLGeneralVariable) {
          final XSLGeneralVariable v = (XSLGeneralVariable)binding;
          final String id = v.getSystemId();
          variables.add(new VariableImpl(realname, convertValue(value), isGlobal, kind, id != null ? id.replaceAll(" ", "%20") : null,
                                         v.getLineNumber()));
        } else {
          variables.add(new VariableImpl(realname, convertValue(value), isGlobal, kind, null, -1));
        }
      } catch (XPathException e) {
        // this should never happen I guess...
        e.printStackTrace();
      }
    }
  }

  private static class MyValue implements Value {
    private final Object myValue;
    private final Type myType;

    public MyValue(Object value, String type) {
      myValue = value;
      myType = new ObjectType(type);
    }

    public MyValue(Object value, int type) {
      myValue = value;
      myType = mapType(type);
    }

    public Object getValue() {
      return myValue;
    }

    public Type getType() {
      return myType;
    }

    private static Type mapType(int type) {
      switch (type) {
        case com.icl.saxon.expr.Value.BOOLEAN:
          return XPathType.BOOLEAN;
        case com.icl.saxon.expr.Value.NODESET:
          return XPathType.NODESET;
        case com.icl.saxon.expr.Value.NUMBER:
          return XPathType.NUMBER;
        case com.icl.saxon.expr.Value.STRING:
          return XPathType.STRING;
        case com.icl.saxon.expr.Value.OBJECT:
          return XPathType.OBJECT;
        default:
          return XPathType.UNKNOWN;
      }
    }

    public static Value create(com.icl.saxon.expr.Value v, Context context) throws XPathException {
      if (v instanceof NodeSetValue) {
        if (v instanceof FragmentValue) {
          final FragmentValue value = (FragmentValue)v;
          final boolean b = value.isGeneralUseAllowed();
          try {
            if (!b) value.allowGeneralUse();

            final DocumentInfo node = value.getRootNode();
            final GeneralOutputter outputter = new GeneralOutputter(node.getNamePool());
            final StringWriter writer = new StringWriter();
            final Properties props = new Properties();
            props.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            outputter.setOutputDestination(props, new StreamResult(writer));
            node.copy(outputter);

            return new MyValue(writer.toString(), v.getDataType());
          } catch (TransformerException e) {
            return new MyValue(v.asString(), v.getDataType());
          } finally {
            if (!b && fGeneralUseAllowed != null) {
              resetGeneralUseAllowed(value);
            }
          }
        } else if (v instanceof TextFragmentValue) {
          // this really is just a string
          return new MyValue(v.asString(), com.icl.saxon.expr.Value.STRING);
        }

        final List<Node> list = new ArrayList<Node>();
        final NodeEnumeration nodeEnumeration = ((NodeSetValue)v).enumerate();
        while (nodeEnumeration.hasMoreElements()) {
          final NodeInfo node = nodeEnumeration.nextElement();
          final String path = Navigator.getPath(node);
          final String id = node.getSystemId();
          if (id != null) {
            try {
              list.add(new Node(new URI(id.replaceAll(" ", "%20")).normalize().toASCIIString(), node.getLineNumber(), path,
                                node.getStringValue()));
            } catch (URISyntaxException e) {
              debug(e);
              list.add(new Node(id, node.getLineNumber(), path, node.getStringValue()));
            }
          } else {
            list.add(new Node(null, -1, path, node.getStringValue()));
          }
        }
        return new MyValue(new NodeSet(v.asString(), list), v.getDataType());
      } else if (v instanceof ObjectValue) {
        final Object o = ((ObjectValue)v).getObject();
        return new MyValue(o, o != null ? o.getClass().getName() : "null");
      } else if (v != null) {
        return new MyValue(v.evaluateAsString(context), v.getDataType());
      } else {
        return new MyValue("", "<uninitialized>");
      }
    }
  }

  private static void resetGeneralUseAllowed(FragmentValue value) {
    try {
      fGeneralUseAllowed.set(value, false);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  private static class MyDummyElement extends StyleElement {
    private final StyleElement myElement;

    public MyDummyElement(StyleElement element) {
      myElement = element;
      substituteFor(element);
    }

    public void prepareAttributes() throws TransformerConfigurationException {
    }

    public void process(Context context) throws TransformerException {
    }

    public StaticContext getStaticContext() {
      return new ExpressionContext(this);
    }

    @Override
    public Node getPreviousSibling() {
      return myElement.getPreviousSibling();
    }
  }
}
