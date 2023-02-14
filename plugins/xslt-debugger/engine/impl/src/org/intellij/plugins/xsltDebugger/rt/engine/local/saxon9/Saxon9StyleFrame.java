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

package org.intellij.plugins.xsltDebugger.rt.engine.local.saxon9;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.TraceExpression;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.FloatValue;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableComparator;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableImpl;

import java.util.ArrayList;
import java.util.List;

class Saxon9StyleFrame extends AbstractSaxon9Frame<Debugger.StyleFrame, TraceExpression> implements Debugger.StyleFrame {

  private final XPathContext myXPathContext;

  protected Saxon9StyleFrame(Debugger.StyleFrame prev, TraceExpression element, XPathContext xPathContext) {
    super(prev, element);
    myXPathContext = xPathContext;
  }

  public String getInstruction() {
    return myElement.getExpressionName();
  }

  public Value eval(String expr) throws Debugger.EvaluationException {
    assert isValid();

    Saxon9TraceListener.MUTED = true;
    try {
      return createValue(new ExpressionFacade(myElement));
    } catch (AssertionError | Exception e) {
      debug(e);
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    }
    finally {
      Saxon9TraceListener.MUTED = false;
    }
  }

  private Value createValue(ValueFacade expression) throws XPathException {
    final TypeHierarchy typeHierarchy = myXPathContext.getConfiguration().getTypeHierarchy();
    final ItemType itemType = expression.getItemType(typeHierarchy);

    final SequenceIterator it = expression.iterate(myXPathContext);
    GroundedValue value = null;
    if (it.next() != null) {
      value = it.materialize();
    }
    if (it.next() == null) {
      return new SingleValue(value, itemType);
    }
    return new SequenceValue(value, it, itemType);
  }

  public List<Debugger.Variable> getVariables() {
    assert isValid();

    Saxon9TraceListener.MUTED = true;
    final ArrayList<Debugger.Variable> variables;
    try {
      variables = collectVariables();
    } finally {
      Saxon9TraceListener.MUTED = false;
    }

    variables.sort(VariableComparator.INSTANCE);

    return variables;
  }

  private ArrayList<Debugger.Variable> collectVariables() {
    final ArrayList<Debugger.Variable> variables = new ArrayList<>();

    Iterable<PackageData> packages = myXPathContext.getController().getExecutable().getPackages();
    for (PackageData data : packages) {
      List<GlobalVariable> globalVariables = data.getGlobalVariableList();
      for (GlobalVariable globalVariable : globalVariables) {

        final Value value = createVariableValue(new GlobalVariableFacade(globalVariable));
        final int lineNumber = globalVariable.getLineNumber();
        final String systemId = globalVariable.getSystemId();
        variables.add(new VariableImpl(globalVariable.getVariableQName().getDisplayName(), value, true, Debugger.Variable.Kind.VARIABLE, systemId, lineNumber));
      }
    }

    XPathContext context = myXPathContext;
    while (context != null) {
      final StackFrame frame = context.getStackFrame();
      final SlotManager map = frame.getStackFrameMap();

      final Sequence[] values = frame.getStackFrameValues();

      outer:
      for (int i = 0, valuesLength = values.length; i < valuesLength; i++) {
        final Sequence value = values[i];
        if (value != null) {
          final String name = map.getVariableMap().get(i).getDisplayName();
          for (Debugger.Variable variable : variables) {
            if (name.equals(variable.getName())) {
              continue outer;
            }
          }

          GroundedValue groundedValue;
          try {
            groundedValue = value.materialize();
          }
          catch (XPathException e) {
            continue outer;
          }
          Value localValue = createVariableValue(new LocalVariableFacade(groundedValue));
          variables.add(new VariableImpl(name, localValue, false, Debugger.Variable.Kind.VARIABLE, "", -1));
        }
      }

      context = context.getCaller();
    }
    return variables;
  }

  private Value createVariableValue(ValueFacade facade) {
    try {
      return createValue(facade);
    } catch (XPathException e) {
      return new ErrorValue(e.getMessage(), facade);
    }
  }

  private static class SingleValue implements Value {
    private final GroundedValue myValue;
    private final ItemType myItemType;

    SingleValue(GroundedValue value, ItemType itemType) {
      myValue = value;
      myItemType = itemType;
    }

    public Object getValue() {
      try {
        return myValue != null ? myValue.getStringValue() : null;
      }
      catch (XPathException e) {
        return null;
      }
    }

    public Type getType() {
      return new ObjectType(myItemType.toString());
    }
  }

  private static class SequenceValue implements Value {
    private final String myValue;
    private final ItemType myItemType;

    SequenceValue(GroundedValue value, SequenceIterator it, ItemType type) throws XPathException {
      String s = "(" + value.getStringValue() + ", " + it.materialize().getStringValue();
      while (it.next() != null) {
        s += ", " + it.materialize().getStringValue();
      }
      s += ")";
      myValue = s;
      myItemType = type;
    }

    public Object getValue() {
      return myValue;
    }

    public Type getType() {
      return new ObjectType(myItemType.toString() + "+");
    }
  }

  private interface ValueFacade {
    ItemType getItemType(TypeHierarchy hierarchy);

    SequenceIterator iterate(XPathContext context) throws XPathException;
  }

  private static class ExpressionFacade implements ValueFacade {
    private final Expression myExpression;

    ExpressionFacade(Expression expression) {
      myExpression = expression;
    }

    public ItemType getItemType(TypeHierarchy hierarchy) {
      return myExpression.getItemType();
    }

    public SequenceIterator iterate(XPathContext context) throws XPathException {
      return myExpression.iterate(context);
    }
  }

  private static class GlobalVariableFacade implements ValueFacade {
    private final GlobalVariable myVariable;

    GlobalVariableFacade(GlobalVariable variable) {
      myVariable = variable;
    }

    public ItemType getItemType(TypeHierarchy hierarchy) {
      return myVariable.getRequiredType().getPrimaryType();
    }

    public SequenceIterator iterate(XPathContext context) throws XPathException {
      List<ComponentBinding> bindings = context.getCurrentComponent().getComponentBindings();
      if (bindings.size() <= myVariable.getBinderySlotNumber()) return EmptyIterator.emptyIterator();
      GroundedValue<?> groundedValue = myVariable.evaluateVariable(context);
      return groundedValue.iterate();
    }
  }

  private static class LocalVariableFacade implements ValueFacade {
    private final GroundedValue myValue;

    LocalVariableFacade(GroundedValue value) {
      myValue = value;
    }

    public ItemType getItemType(TypeHierarchy hierarchy) {
      if (myValue instanceof FloatValue) {
        return ((FloatValue)myValue).getItemType();
      }
      if (myValue instanceof Item) {
        return Type.getItemType((Item)myValue, hierarchy);
      }
      return AnyItemType.getInstance();
    }

    public SequenceIterator iterate(XPathContext context) {
      return myValue.iterate();
    }
  }

  private class ErrorValue implements Value {
    private final String myError;
    private final ValueFacade myFacade;

    ErrorValue(String error, ValueFacade facade) {
      myError = error;
      myFacade = facade;
    }

    public Object getValue() {
      return " - error: " + myError + " - ";
    }

    public Type getType() {
      return new ObjectType(myFacade.getItemType(myXPathContext.getConfiguration().getTypeHierarchy()).toString());
    }
  }
}
