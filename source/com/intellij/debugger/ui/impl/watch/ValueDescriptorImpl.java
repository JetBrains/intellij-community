package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;

public abstract class ValueDescriptorImpl extends NodeDescriptorImpl implements ValueDescriptor{
  protected final Project myProject;

  NodeRenderer myRenderer = null;

  NodeRenderer myAutoRenderer = null;

  private Value             myValue;
  private EvaluateException myValueException;

  private String            myValueLabel;

  protected boolean myIsNew = true;
  private boolean myIsDirty = false;
  private boolean myIsLvalue = false;
  private boolean myIsExpandable;

  protected ValueDescriptorImpl(Project project, Value value) {
    myProject = project;
    myValue = value;
  }

  protected ValueDescriptorImpl(Project project) {
    myProject = project;
    myIsNew = true;
  }

  public boolean isArray               () { return getValue() instanceof ArrayReference; }
  public boolean isDirty               () { return myIsDirty; }
  public boolean isLvalue              () { return myIsLvalue; }
  public boolean isNull                () { return getValue() == null; }
  public boolean isPrimitive           () { return getValue() instanceof PrimitiveValue; }
  public boolean isIsNew               () { return myIsNew; }

  public boolean isValueValid() {
    return myValueException == null;
  }

  public Value  getValue        () { return myValue; }

  public boolean isExpandable() {
    return myIsExpandable;
  }

  public abstract Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public final void setContext(EvaluationContextImpl evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    Value value = null;
    try {
      value = calcValue(evaluationContext);

      if(!myIsNew) {
        myIsDirty = (value == null)? myValue != null : !value.equals(myValue);
        if(myValue instanceof DoubleValue && Double.isNaN(((DoubleValue) myValue).doubleValue())){
          myIsDirty = !(value instanceof DoubleValue && Double.isNaN(((DoubleValue) myValue).doubleValue()));
        } else if(myValue instanceof FloatValue && Float.isNaN(((FloatValue) myValue).floatValue())){
          myIsDirty = !(value instanceof FloatValue && Float.isNaN(((FloatValue) myValue).floatValue()));
        }
      }
      myValue = value;
      myValueException = null;
    }
    catch (EvaluateException e) {
      myValueException = e;
      myValue = null;
      myIsExpandable = false;
    }

    myIsNew = false;
  }

  public void setAncestor(NodeDescriptorImpl oldDescriptor) {
    super.setAncestor(oldDescriptor);
    myIsNew = false;
    myValue = ((ValueDescriptorImpl)oldDescriptor).getValue();
  }

  protected void setLvalue (boolean value) {
    myIsLvalue = value;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    if(myValueException == null) {
      myIsExpandable = getRenderer(context.getDebugProcess()).isExpandable(getValue(), context, this);
    }
    else {
      myIsExpandable = false;
    }

    return setValueLabel(calcValueLabel(context, labelListener));
  }

  private String getCustomLabel(String label) {
    //translate only strings in quotes
    StringBuffer buf = new StringBuffer();
    if(getValue() instanceof ObjectReference) {
      String idLabel = makeIdLabel((ObjectReference)getValue());
      if(!label.startsWith(idLabel)) {
        buf.append(idLabel);
      }
    }
    if(label == null) {
      buf.append("null");
    } else {
      if(StringUtil.startsWithChar(label, '\"') && StringUtil.endsWithChar(label, '\"')) {
        buf.append('"');
        buf.append(DebuggerUtils.translateStringValue(label.substring(1, label.length() - 1)));
        buf.append('"');
      } else {
        buf.append(DebuggerUtils.translateStringValue(label));
      }
    }
    return buf.toString();
  }

  private String makeIdLabel(ObjectReference ref) {
    if(ref != null) {
      return getIdLabel(ref);
    } else
      return "";
  }


  public String setValueLabel(String label) {
    String customLabel = getCustomLabel(label);
    myValueLabel = customLabel;

    return setLabel(calcValueName() + " = " + customLabel);
  }

  public String setValueLabelFailed(EvaluateException e) {
    String label = setFailed(e);
    setValueLabel(label);
    return label;
  }

  public abstract String calcValueName();

  private String calcValueLabel(EvaluationContextImpl evaluationContext, final DescriptorLabelListener labelListener) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      if(myValueException != null) {
        throw myValueException;
      }
      return getRenderer(evaluationContext.getDebugProcess()).calcLabel(this, evaluationContext, labelListener);
    }
    catch (EvaluateException e) {
      return setValueLabelFailed(e);
    }
  }

  public void displayAs(NodeDescriptorImpl descriptor) {
    if (descriptor instanceof ValueDescriptorImpl) {
      ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)descriptor;
      myRenderer = valueDescriptor.myRenderer;
    }
    super.displayAs(descriptor);
  }

  public NodeRenderer getLastRenderer() {
    return myRenderer != null ? myRenderer: myAutoRenderer;
  }

  public Type getType() {
    Value value = getValue();
    return value != null ? value.type() : null;
  }

  public NodeRenderer getRenderer (DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    Type type = getType();
    if(type != null && myRenderer != null && myRenderer.isApplicable(type))
      return myRenderer;

    myAutoRenderer = debugProcess.getNodeRendererManager().getAutoRenderer(this);
    return myAutoRenderer;
  }

  public void setRenderer   (NodeRenderer renderer) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myRenderer = renderer;
    myAutoRenderer = null;
  }

  //returns expression that evaluates tree to this descriptor
  public PsiExpression getTreeEvaluation(DebuggerTreeNodeImpl debuggerTreeNode, DebuggerContextImpl context) throws EvaluateException {
    if(debuggerTreeNode.getParent() != null && debuggerTreeNode.getParent().getDescriptor() instanceof ValueDescriptor) {
      NodeDescriptorImpl descriptor = ((DebuggerTreeNodeImpl)debuggerTreeNode.getParent()).getDescriptor();
      ValueDescriptorImpl vDescriptor = ((ValueDescriptorImpl)descriptor);
      PsiExpression parentEvaluation = vDescriptor.getTreeEvaluation((DebuggerTreeNodeImpl)debuggerTreeNode.getParent(), context);

      return DebuggerTreeNodeExpression.substituteThis(
              vDescriptor.getRenderer(context.getDebugProcess()).getChildrenValueExpression(debuggerTreeNode, context),
              parentEvaluation, vDescriptor.getValue());
    }
    else {
      return getDescriptorEvaluation(context);
    }
  }

  //returns expression that evaluates descriptor value
  //use 'this' to reference parent node
  //for ex. FieldDescriptorImpl should return
  //this.fieldName
  public abstract PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

  public static String getIdLabel(ObjectReference objRef) {
    StringBuffer buf = new StringBuffer();
    buf.append('{');
    buf.append(objRef.type().name()).append('@');
    if(ApplicationManager.getApplication().isUnitTestMode()) {
      buf.append("uniqueID");
    }
    else {
      buf.append(objRef.uniqueID());
    }
    buf.append('}');

    if (objRef instanceof ArrayReference) {
      int idx = buf.indexOf("[");
      if(idx >= 0) {
        buf.insert(idx + 1, Integer.toString(((ArrayReference)objRef).length()));
      }
    }

    return buf.toString();
  }

  public boolean canSetValue() {
    return !myIsSynthetic && isLvalue();
  }

  public String getValueLabel() {
    return myValueLabel;
  }

  //Context is set to null
  public void clear() {
    super.clear();
    setValueLabel("");
    myIsExpandable = false;
  }
}