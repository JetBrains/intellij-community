package com.intellij.debugger.ui.impl.watch.render;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.debugger.DebuggerContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.*;
import org.jdom.Element;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 3:07:27 PM
 */
public class PrimitiveRenderer implements NodeRenderer, Cloneable {
  public static final String UNIQUE_ID = "PrimitiveRenderer";

  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.render.PrimitiveRenderer");

  public PrimitiveRenderer() {
  }

  public RendererProvider getRendererProvider() {
    return DefaultRendererProvider.getInstance();
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public String getName() {
    return "Default";
  }

  public void setName(String text) {
    LOG.assertTrue(false, "Cannot set name for primitive renderer");
  }

  public PrimitiveRenderer clone(){
    try {
      return (PrimitiveRenderer)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.assertTrue(false);
      return null;
    }
  }

  public boolean isApplicable(Type type) {
    if(type == null) return true;
    return type instanceof PrimitiveType || type instanceof VoidType;
  }

  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) {
    Value value = valueDescriptor.getValue();
    if(value == null) {
      return "null";
    } else if (value instanceof PrimitiveValue) {
      StringBuffer buf = new StringBuffer(16);
      if (value instanceof CharValue) {
        buf.append("'");
        buf.append(DebuggerUtilsEx.translateStringValue(value.toString()));
        buf.append("' ");
        long longValue = ((PrimitiveValue)value).longValue();
        buf.append(Long.toString(longValue));
      }
      else if (value instanceof ByteValue) {
        buf.append(value.toString());
      }
      else if (value instanceof ShortValue) {
        buf.append(value.toString());
      }
      else if (value instanceof IntegerValue) {
        buf.append(value.toString());
      }
      else if (value instanceof LongValue) {
        buf.append(value.toString());
      }
      else {
        buf.append(DebuggerUtilsEx.translateStringValue(value.toString()));
      }
      return buf.toString();
    } else {
      return "undefined";
    }
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public ChildrenRenderer getChildrenRenderer() {
    return this;
  }

  public ValueLabelRenderer getLabelRenderer() {
    return this;
  }

  public PsiExpression getChildrenValueExpression(DebuggerTreeNode node, DebuggerContext context) {
    LOG.assertTrue(false);
    return null;
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return false;
  }
}
