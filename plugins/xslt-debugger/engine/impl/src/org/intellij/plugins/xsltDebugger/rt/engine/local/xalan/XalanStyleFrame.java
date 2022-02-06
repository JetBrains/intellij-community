// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.xsltDebugger.rt.engine.local.xalan;

import org.apache.xalan.templates.ElemLiteralResult;
import org.apache.xalan.templates.ElemParam;
import org.apache.xalan.templates.ElemTemplateElement;
import org.apache.xalan.templates.ElemVariable;
import org.apache.xalan.trace.TracerEvent;
import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMIterator;
import org.apache.xml.dtm.ref.DTMNodeProxy;
import org.apache.xml.utils.PrefixResolver;
import org.apache.xml.utils.PrefixResolverDefault;
import org.apache.xpath.XPath;
import org.apache.xpath.XPathContext;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.AbstractFrame;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableComparator;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableImpl;
import org.w3c.dom.Node;

import javax.xml.transform.TransformerException;
import java.util.*;

class XalanStyleFrame extends AbstractFrame<Debugger.StyleFrame> implements Debugger.StyleFrame {
  private final boolean myWithSourceFrame;

  private final TransformerImpl myTransformer;
  private final ElemTemplateElement myCurrentElement;
  private final XPathContext myContext;
  private final int myCurrentNode;

  private final int myLineNumber;
  private final String myURI;
  private final String myInstr;

  XalanStyleFrame(TracerEvent ev, Debugger.StyleFrame currentFrame, boolean withSourceFrame) {
    super(currentFrame);
    myWithSourceFrame = withSourceFrame;

    myInstr = getInstruction(ev.m_styleNode);

    myLineNumber = ev.m_styleNode.getLineNumber();
    if (ev.m_styleNode.getSystemId() != null) {
      myURI = ev.m_styleNode.getSystemId();
    } else if (currentFrame != null && currentFrame.getURI() != null) {
      myURI = currentFrame.getURI();
    } else {
      myURI = ev.m_processor.getStylesheet().getSystemId();
    }
    myTransformer = ev.m_processor;

    myCurrentElement = myTransformer.getCurrentElement();
    myContext = ev.m_processor.getXPathContext();
    myCurrentNode = myContext.getCurrentNode();
  }

  private void addVariable(ElemVariable variable, boolean global, Collection<Debugger.Variable> variables) {
    final Debugger.Variable.Kind kind = variable instanceof ElemParam ?
                                        Debugger.Variable.Kind.PARAMETER : Debugger.Variable.Kind.VARIABLE;

    assert global == variable.getIsTopLevel() : global + " vs. " + variable.getIsTopLevel() + " (" + variable.getName() + ")";

    final String name = variable.getName().getLocalName();
    try {
      final Value value = kind == Debugger.Variable.Kind.PARAMETER ?
                          eval("$" + variable.getName().toString()) : // http://youtrack.jetbrains.net/issue/IDEA-78638
                          new XObjectValue(variable.getValue(myTransformer, myCurrentNode));

      variables.add(new VariableImpl(name, value, global, kind, variable.getSystemId(), variable.getLineNumber()));
    } catch (TransformerException | Debugger.EvaluationException e) {
      debug(e);
    }
  }

  public boolean isWithSourceFrame() {
    return myWithSourceFrame;
  }

  public String getInstruction() {
    return myInstr;
  }

  public List<Debugger.Variable> getVariables() {
    assert isValid();

    return collectVariables();
  }

  private List<Debugger.Variable> collectVariables() {
    final Set<Debugger.Variable> variables = new HashSet<>();

    ElemTemplateElement p = myCurrentElement;
    while (p != null) {
      ElemTemplateElement s = p;
      while ((s = s.getPreviousSiblingElem()) != null) {
        if (s instanceof ElemVariable) {
          final ElemVariable variable = (ElemVariable)s;
          if (variable.getIsTopLevel()) {
            continue;
          }

          addVariable(variable, false, variables);
        }
      }
      p = p.getParentElem();
    }

    @SuppressWarnings({"unchecked", "UseOfObsoleteCollectionType"})
    final Vector<ElemVariable> globals = myTransformer.getStylesheet().getVariablesAndParamsComposed();
    for (ElemVariable variable : globals) {
      addVariable(variable, true, variables);
    }

    final ArrayList<Debugger.Variable> result = new ArrayList<>(variables);
    result.sort(VariableComparator.INSTANCE);
    return result;
  }

  public String getURI() {
    return myURI;
  }

  public int getLineNumber() {
    return myLineNumber;
  }

  public Value eval(String expr) throws Debugger.EvaluationException {
    assert isValid();

    try {
      final DTMIterator context = myTransformer.getContextNodeList();

      final int ctx;
      final DTM dtm = context.getDTM(myCurrentNode);
      if (dtm.getDocumentRoot(myCurrentNode) == myCurrentNode) {
        ctx = dtm.getFirstChild(myCurrentNode);
      } else {
        ctx = myCurrentNode;
      }

      final DTMNodeProxy c = new DTMNodeProxy(dtm, ctx);
      final PrefixResolver prefixResolver = new PrefixResolverDefault(c) {
        @Override
        public String getNamespaceForPrefix(String prefix, Node context) {
          if (context instanceof DTMNodeProxy) {
            final DTMNodeProxy proxy = (DTMNodeProxy)context;
            final DTM dtm = proxy.getDTM();
            int p = proxy.getDTMNodeNumber();
            while (p != DTM.NULL) {
              int nsNode = dtm.getFirstNamespaceNode(p, true);
              while (nsNode != DTM.NULL) {
                final String s = dtm.getLocalName(nsNode);
                if (s.equals(prefix)) {
                  return dtm.getNodeValue(nsNode);
                }
                nsNode = dtm.getNextNamespaceNode(p, nsNode, true);
              }
              p = dtm.getParent(p);
            }
          }
          return super.getNamespaceForPrefix(prefix, context);
        }
      };

      final XPath xPath = new XPath(expr, myCurrentElement, prefixResolver, XPath.SELECT, myTransformer.getErrorListener());
      return new XObjectValue(xPath.execute(myContext, myCurrentNode, myCurrentElement));
    } catch (Exception e) {
      debug(e);
      final String message = e.getMessage();
      throw new Debugger.EvaluationException(message != null ? message : e.getClass().getSimpleName());
    }
  }

  static String getInstruction(ElemTemplateElement node) {
    final String name = node.getNodeName();
    if (node instanceof ElemLiteralResult) {
      return name;
    } else if (name != null && name.indexOf(':') == -1) {
      return "xsl:" + name;
    }
    return name;
  }
}
