package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.*;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * User: lex
 * Date: Sep 17, 2003
 * Time: 2:04:00 PM
 */
public class ClassRenderer extends NodeRendererImpl{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.ClassRenderer");
  
  public static final String UNIQUE_ID = "ClassRenderer";

  public boolean SORT_ASCENDING               = false;
  public boolean SHOW_SYNTHETICS              = true;
  public boolean SHOW_STATIC                  = false;
  public boolean SHOW_STATIC_FINAL            = false;
  public boolean CHILDREN_PHYSICAL            = true;

  public ClassRenderer() {
    myProperties.setEnabled(true);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public boolean isEnabled() {
    return myProperties.isEnabled();
  }

  public void setEnabled(boolean enabled) {
    myProperties.setEnabled(enabled);
  }

  public ClassRenderer clone() {
    return (ClassRenderer) super.clone();
  }

  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)  throws EvaluateException {
    return calcLabel(descriptor);
  }

  protected static String calcLabel(ValueDescriptor descriptor) {
    ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
    Value value = valueDescriptor.getValue();
    if (value instanceof ObjectReference) {
      StringBuffer buf = new StringBuffer(32);
      if (value instanceof StringReference) {
        buf.append('\"');
        buf.append(((StringReference)value).value());
        buf.append('\"');
      }
      else if (value instanceof ClassObjectReference) {
        ReferenceType type = ((ClassObjectReference)value).reflectedType();
        buf.append((type != null)?type.name():"{...}");
      }
      else {
        buf.append(ValueDescriptorImpl.getIdLabel((ObjectReference)value));
      }
      return buf.toString();
    }
    else if(value == null) {
      return "null";
    }
    else {
      return "undefined";
    }
  }

  public void buildChildren(final Value value, final ChildrenBuilder builder, final EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final ValueDescriptorImpl parentDescriptor = (ValueDescriptorImpl)builder.getParentDescriptor();
    final NodeManager nodeManager = builder.getNodeManager();
    final NodeDescriptorFactory nodeDescriptorFactory = builder.getDescriptorManager();

    List<DebuggerTreeNode> children = new ArrayList<DebuggerTreeNode>();
    if (value instanceof ObjectReference) {
      final ObjectReference objRef = (ObjectReference)value;
      final ReferenceType refType = objRef.referenceType();
      // default ObjectReference processing
      final List fields = refType.allFields();
      if (fields.size() > 0) {
        for (Iterator it = fields.iterator(); it.hasNext();) {
          Field jdiField = (Field)it.next();
          if (!shouldDisplay(jdiField)) {
            continue;
          }
          children.add(nodeManager.createNode(nodeDescriptorFactory.getFieldDescriptor(parentDescriptor, objRef, jdiField), evaluationContext));
        }

        if(SORT_ASCENDING) {
          Collections.sort(children, NodeManagerImpl.getNodeComparator());
        }
      }
      else {
        children.add(nodeManager.createMessageNode(MessageDescriptor.CLASS_HAS_NO_FIELDS.getLabel()));
      }
    }
    builder.setChildren(children);
  }

  private boolean isSynthetic(TypeComponent typeComponent) {
    if (typeComponent == null) return false;
    VirtualMachine machine = typeComponent.virtualMachine();
    return machine != null && machine.canGetSyntheticAttribute() && typeComponent.isSynthetic();
  }


  private boolean shouldDisplay(TypeComponent component) {
    if (!SHOW_SYNTHETICS && isSynthetic(component)) {
      return false;
    }
    if (!(component instanceof Field)) {
      return true;
    }
    Field field = (Field)component;

    if(!SHOW_STATIC && field.isStatic()) return false;

    if(!SHOW_STATIC_FINAL && field.isStatic() && field.isFinal()) return false;

    return true;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    FieldDescriptor fieldDescriptor = (FieldDescriptor)node.getDescriptor();

    PsiElementFactory elementFactory = PsiManager.getInstance(node.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(fieldDescriptor.getField().name(), DebuggerUtils.findClass(fieldDescriptor.getObject().referenceType().name(), context.getProject()));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException("Invalid field name '" + fieldDescriptor.getField().name() + "'", null);
    }
  }

  private static boolean valueExpandable(Value value)  {
    try {
      if(value instanceof ArrayReference) {
        return ((ArrayReference)value).length() > 0;
      }
      else if(value instanceof ObjectReference) {
        return ((ObjectReference)value).referenceType().allFields().size() > 0;
      }
    }
    catch (ObjectCollectedException e) {
      return true;
    }

    return false;
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return valueExpandable(value);
  }

  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && !(type instanceof ArrayType);
  }

  public String getName() {
    return "Object";
  }

  public void setName(String text) {
    LOG.assertTrue(false);
  }
}
