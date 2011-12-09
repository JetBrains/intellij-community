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
import net.sf.saxon.expr.instruct.GeneralVariable;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.LocalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.*;
import net.sf.saxon.style.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.intellij.plugins.xsltDebugger.rt.engine.Value;
import org.intellij.plugins.xsltDebugger.rt.engine.local.VariableImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    Saxon9TraceListener.MUTED = true;
    try {
      Expression expression =
        ExpressionTool.make(expr, new EvalContext(myElement), myElement, 0, Token.EOF, 0, false);

      final ExpressionVisitor visitor = ExpressionVisitor.make(myElement.getStaticContext(), expression.getExecutable());
      expression = expression.typeCheck(visitor, Type.ITEM_TYPE);
      final int variables = myXPathContext.getStackFrame().getStackFrameMap().getNumberOfVariables();
      ExpressionTool.allocateSlots(expression, variables, myElement.getContainingSlotManager());

      final TypeHierarchy typeHierarchy = myXPathContext.getConfiguration().getTypeHierarchy();
      final ItemType itemType = expression.getItemType(typeHierarchy);

      final SequenceIterator it = expression.iterate(myXPathContext);
      Item value = null;
      if (it.next() != null) {
        value = it.current();
      }
      if (it.next() == null) {
        return new SingleValue(value, itemType);        
      }
      return new SequenceValue(value, it, itemType);
    } catch (AssertionError e) {
      assert debug(e);
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    } catch (Exception e) {
      assert debug(e);
      throw new Debugger.EvaluationException(e.getMessage() != null ? e.getMessage() : e.toString());
    } finally {
      Saxon9TraceListener.MUTED = false;
    }
  }

  @SuppressWarnings("CallToPrintStackTrace")
  private static boolean debug(Throwable e) {
    e.printStackTrace();
    return true;
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

      outer:
      for (int i = 0, valuesLength = values.length; i < valuesLength; i++) {
        final ValueRepresentation value = values[i];
        if (value != null) {
          final String name = map.getVariableMap().get(i).getDisplayName();
          for (Debugger.Variable variable : variables) {
            if (name.equals(variable.getName())) {
              continue outer;
            }
          }

          variables.add(new VariableImpl(name, new Value() {
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

  private static class SingleValue implements Value {
    private final Item myValue;
    private final ItemType myItemType;

    public SingleValue(Item value, ItemType itemType) {
      myValue = value;
      myItemType = itemType;
    }

    public Object getValue() {
      return myValue != null ? myValue.getStringValue() : null;
    }

    public Type getType() {
      return new ObjectType(myItemType.toString());
    }
  }

  private static class SequenceValue implements Value {
    private final String myValue;
    private final ItemType myItemType;

    public SequenceValue(Item value, SequenceIterator it, ItemType type) throws XPathException {
      String s = "(" + value.getStringValue() + ", " + it.current().getStringValue();
      while (it.next() != null) {
        s += ", " + it.current().getStringValue();
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

  private static class EvalContext extends ExpressionContext {

    private static Field myRedundant;
    static {
      try {
        myRedundant = XSLGeneralVariable.class.getDeclaredField("redundant");
        myRedundant.setAccessible(true);
      } catch (Exception e) {
        e.printStackTrace();
        myRedundant = null;
      }
    }

    private final StyleElement myElement;

    public EvalContext(final StyleElement element) {
      super(element);
      myElement = element;
    }

    @Override
    public FunctionLibrary getFunctionLibrary() {
      return new FunctionLibraryWrapper(myElement);
    }

    @Override
    public Expression bindVariable(StructuredQName qName) throws XPathException {
      final VariableReference expression = (VariableReference)super.bindVariable(qName);
      final XSLVariableDeclaration declaration = myElement.bindVariable(qName);
      final Declaration decl = new Declaration(declaration.getPrincipalStylesheetModule(), myElement);
      final GeneralVariable  var;
      final Boolean prev = setRedundant(declaration, Boolean.FALSE);
      try {
        if (declaration instanceof XSLVariable) {
          final XSLVariable variable = (XSLVariable)declaration;
          if (declaration.isGlobal()) {
            var = (GlobalVariable)variable.compile(declaration.getExecutable(), decl);
          } else {
            var = (LocalVariable)variable.compileLocalVariable(declaration.getExecutable(), decl);
          }
        } else if (declaration instanceof XSLParam) {
          var = (GeneralVariable)declaration.compile(declaration.getExecutable(), decl);
        } else {
          return expression;
        }
      } finally {
        setRedundant(declaration, prev);
      }
      expression.fixup(var);
      return expression;
    }

    private static Boolean setRedundant(XSLVariableDeclaration variable, Boolean value) {
      if (myRedundant == null) return null;

      Object o = Boolean.FALSE;
      try {
        o = myRedundant.get(variable);
        myRedundant.set(variable, value);
      } catch (IllegalAccessException e) {
        debug(e);
      }
      return (Boolean)o;
    }

    private static class FunctionLibraryWrapper implements FunctionLibrary {
      private static Method myGetFunction;
      static {
        try {
          myGetFunction = PrincipalStylesheetModule.class.getDeclaredMethod("getFunction", StructuredQName.class, int.class);
          myGetFunction.setAccessible(true);
        } catch (Exception e) {
          e.printStackTrace();
          myGetFunction = null;
        }
      }

      private final FunctionLibrary myLibrary;
      private final PrincipalStylesheetModule myModule;

      public FunctionLibraryWrapper(StyleElement element) {
        myLibrary = element.getStaticContext().getFunctionLibrary();
        myModule = element.getPrincipalStylesheetModule();
      }

      public SequenceType[] getFunctionSignature(StructuredQName functionName, int arity) {
        return myLibrary.getFunctionSignature(functionName, arity);
      }

      public Expression bind(StructuredQName functionName, Expression[] staticArgs, StaticContext env, Container container)
        throws XPathException {
        final Expression call = myLibrary.bind(functionName, staticArgs, env, container);
        if (call instanceof UserFunctionCall) {
          final XSLFunction fn = getFunction(functionName, staticArgs);
          if (fn != null) {
            ((UserFunctionCall)call).setFunction(fn.getCompiledFunction());
          }
        }
        return call;
      }

      private XSLFunction getFunction(StructuredQName functionName, Expression[] staticArgs) {
        if (myGetFunction == null) return null;
        try {
          return (XSLFunction)myGetFunction.invoke(myModule, functionName, staticArgs.length);
        } catch (Exception e) {
          debug(e);
          return null;
        }
      }

      public FunctionLibrary copy() {
        return this;
      }
    }
  }
}
