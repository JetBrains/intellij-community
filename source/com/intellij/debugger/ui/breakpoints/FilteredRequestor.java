/**
 * class FilteredRequestor
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.requests.LocatableEventRequestor;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

public abstract class FilteredRequestor implements LocatableEventRequestor, JDOMExternalizable {
  public boolean COUNT_FILTER_ENABLED     = false;
  public int COUNT_FILTER = 0;

  public boolean CONDITION_ENABLED        = false;
  private TextWithImportsImpl myCondition       = TextWithImportsImpl.EMPTY;

  public boolean CLASS_FILTERS_ENABLED    = false;
  private ClassFilter[] myClassFilters          = ClassFilter.EMPTY_ARRAY;
  private ClassFilter[] myClassExclusionFilters = ClassFilter.EMPTY_ARRAY;

  public boolean INSTANCE_FILTERS_ENABLED = false;
  protected InstanceFilter[] myInstanceFilters  = InstanceFilter.EMPTY_ARRAY;

  public FilteredRequestor() {
  }

  public InstanceFilter[] getInstanceFilters() {
    return myInstanceFilters;
  }

  public void setInstanceFilters(InstanceFilter[] instanceFilters) {
    myInstanceFilters = instanceFilters != null? instanceFilters : InstanceFilter.EMPTY_ARRAY;
  }

  /**
   * @return true if the ID was added or false otherwise
   */
  public boolean hasObjectID(long id) {
    for (int i = 0; i < myInstanceFilters.length; i++) {
      InstanceFilter instanceFilter = myInstanceFilters[i];
      if (instanceFilter.getId() == id) {
        return true;
      }
    }
    return false;
  }

  protected void addInstanceFilter(long l) {
    final InstanceFilter[] filters = new InstanceFilter[myInstanceFilters.length + 1];
    System.arraycopy(myInstanceFilters, 0, filters, 0, myInstanceFilters.length);
    filters[myInstanceFilters.length] = InstanceFilter.create("" + l);
    myInstanceFilters = filters;
  }

  public final ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  public final void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters != null? classFilters : ClassFilter.EMPTY_ARRAY;
  }

  public ClassFilter[] getClassExclusionFilters() {
    return myClassExclusionFilters;
  }

  public void setClassExclusionFilters(ClassFilter[] classExclusionFilters) {
    myClassExclusionFilters = classExclusionFilters != null? classExclusionFilters : ClassFilter.EMPTY_ARRAY;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);
    String condition = JDOMExternalizerUtil.readField(parentNode, "myCondition");
    if (condition != null) {
      setCondition(TextWithImportsImpl.createExpressionText(condition));
    }

    myClassFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren("filter"));
    myClassExclusionFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren("exclusion_filter"));

    final ClassFilter [] instanceFilters = DebuggerUtilsEx.readFilters(parentNode.getChildren("instance_id"));
    final List<InstanceFilter> iFilters = new ArrayList<InstanceFilter>(instanceFilters.length);

    for (int i = 0; i < instanceFilters.length; i++) {
      try {
        iFilters.add(InstanceFilter.create(instanceFilters[i]));
      }
      catch (Exception e) {
      }
    }
    myInstanceFilters = iFilters.toArray(new InstanceFilter[iFilters.size()]);
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);
    JDOMExternalizerUtil.writeField(parentNode, "myCondition", getCondition().saveToString());
    DebuggerUtilsEx.writeFilters(parentNode, "filter", myClassFilters);
    DebuggerUtilsEx.writeFilters(parentNode, "exclusion_filter", myClassExclusionFilters);
    DebuggerUtilsEx.writeFilters(parentNode, "instance_id", InstanceFilter.createClassFilters(myInstanceFilters));
  }

  public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
    if(COUNT_FILTER_ENABLED) {
      context.getDebugProcess().getVirtualMachineProxy().suspend();
      context.getDebugProcess().getRequestsManager().deleteRequest(this);
      context.getDebugProcess().getRequestsManager().createRequest((Breakpoint)this);
      context.getDebugProcess().getVirtualMachineProxy().resume();
    }
    if (INSTANCE_FILTERS_ENABLED) {
      Value value = context.getThisObject();
      if (value != null) {  // non-static
        ObjectReference reference = (ObjectReference)value;
        if(!hasObjectID(reference.uniqueID())) return false;
      }
    }

    if (CONDITION_ENABLED && getCondition() != null && !"".equals(getCondition())) {
      try {
        ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(context.getProject(), new com.intellij.debugger.EvaluatingComputable<ExpressionEvaluator>() {
          public ExpressionEvaluator compute() throws EvaluateException {
            return EvaluatorBuilderImpl.getInstance().build(getCondition(), getEvaluationElement());
          }
        });
        Value value = evaluator.evaluate(context);
        if (!(value instanceof BooleanValue)) {
          throw EvaluateExceptionUtil.createEvaluateException("Type mismatch. Required boolean value");
        }
        if(!((BooleanValue)value).booleanValue()) return false;
      } catch (EvaluateException ex) {
        if(ex.getCause() instanceof VMDisconnectedException) return false;
        StringBuffer text = new StringBuffer();
        text.append("Failed to evaluate breakpoint condition\n'");
        text.append(getCondition());
        text.append("'\nReason: ");
        text.append(ex.getMessage());
        throw EvaluateExceptionUtil.createEvaluateException(text.toString());
      }
      return true;
    }

    return true;
  }

  public abstract PsiElement getEvaluationElement();

  public TextWithImportsImpl getCondition() {
    return myCondition;
  }

  public void setCondition(TextWithImportsImpl condition) {
    myCondition = condition;
  }
}