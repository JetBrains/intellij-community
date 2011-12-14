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

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.ExpressionVisitor;
import net.sf.saxon.expr.StackFrame;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ValueRepresentation;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 02.06.2007
 */
class Saxon9StyleFrame<N extends StyleElement> extends AbstractSaxon9Frame<Debugger.StyleFrame, N> implements Debugger.StyleFrame {

  private final XPathContext myXPathContext;
  private final StackFrame myStackFrame;

  protected Saxon9StyleFrame(Debugger.StyleFrame prev, N element, XPathContext xPathContext) {
    super(prev, element);
    myXPathContext = xPathContext;
    myStackFrame = myXPathContext.getStackFrame();
  }

  public String getInstruction() {
    return myElement.getDisplayName();
  }

  public Value eval(String expr) throws Debugger.EvaluationException {
    try {
      Expression expression = myElement.makeExpression(expr);
      final TypeHierarchy typeHierarchy = myXPathContext.getConfiguration().getTypeHierarchy();
      expression = expression.typeCheck(ExpressionVisitor.make(myElement.getStaticContext(), expression.getExecutable()), Type.ITEM_TYPE);
      final ItemType itemType = expression.getItemType(typeHierarchy);
      final Item evaluate = expression.evaluateItem(myXPathContext);
      return new Value() {
        public Object getValue() {
          return evaluate != null ? evaluate.getStringValue() : null;
        }

        public Type getType() {
          return new ObjectType(itemType.toString());
        }
      };
    } catch (IllegalArgumentException e) {
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    } catch (XPathException e) {
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    } catch (Exception e) {
      e.printStackTrace();
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    }
  }

  public List<Debugger.Variable> getVariables() {
    final ArrayList<Debugger.Variable> variables = new ArrayList<Debugger.Variable>();

    final HashMap<StructuredQName,GlobalVariable> globalVariables =
      myXPathContext.getController().getExecutable().getCompiledGlobalVariables();
    if (globalVariables != null) {
      for (StructuredQName name : globalVariables.keySet()) {
        final GlobalVariable globalVariable = globalVariables.get(name);
        variables.add(new VariableImpl(globalVariable.getVariableQName().getDisplayName(), new Value() {
          public Object getValue() {
            try {
              final ValueRepresentation valueRepresentation = globalVariable.evaluateVariable(myXPathContext);
              return valueRepresentation != null ? valueRepresentation.getStringValue() : null;
            } catch (XPathException e) {
              return " - error: " + e.getMessage() + " - ";
            }
          }

          public Type getType() {
            return new ObjectType(globalVariable.getRequiredType().toString());
          }
        }, false, Debugger.Variable.Kind.VARIABLE, "", -1));
      }
    }

    XPathContext context = myXPathContext;
    while (context != null) {
      final StackFrame frame = context.getStackFrame();
      final SlotManager map = frame.getStackFrameMap();
      //final int numberOfVariables = map.getNumberOfVariables();
      //System.out.println("numberOfVariables = " + numberOfVariables);

      //for (int i = 0; i < numberOfVariables; i++) {
      //  final ValueRepresentation valueRepresentation = context.evaluateLocalVariable(i);
      //  System.out.println("valueRepresentation = " + valueRepresentation);
      //}

      final ValueRepresentation[] values = frame.getStackFrameValues();
      //System.out.println("values = " + Arrays.toString(values));

      for (int i = 0, valuesLength = values.length; i < valuesLength; i++) {
        final ValueRepresentation value = values[i];
        if (value != null) {
          variables.add(new VariableImpl(map.getVariableMap().get(i).getDisplayName(), new Value() {
            public Object getValue() {
              try {
                return value.getStringValue();
              } catch (XPathException e) {
                return " - error: " + e.getMessage() + " - ";
              }
            }

            public Type getType() {
              if (value instanceof net.sf.saxon.value.Value) {
                final ItemType type =
                  ((net.sf.saxon.value.Value)value).getItemType(myXPathContext.getConfiguration().getTypeHierarchy());
                return new ObjectType(type.toString());
              } else if (value instanceof NodeInfo) {
                return XPathType.NODESET;
              }
              return XPathType.UNKNOWN;
            }
          }, false, Debugger.Variable.Kind.VARIABLE, "", -1));
        }
      }

      context = context.getCaller();
      //System.out.println("context = " + context);
    }

    return variables;
  }
}
