package com.intellij.debugger.actions;

import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.ui.*;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.render.ValueLabelRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.IJSwingUtilities;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.sun.jdi.*;

import javax.swing.*;

/*
 * Class SetValueAction
 * @author Jeka
 */
public class SetValueAction extends DebuggerAction {
  public void update(AnActionEvent e) {
    boolean enable = false;
    DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      NodeDescriptorImpl descriptor = node.getDescriptor();
      if(descriptor instanceof ValueDescriptorImpl){
        ValueDescriptorImpl valueDescriptor = ((ValueDescriptorImpl)descriptor);
        enable = valueDescriptor.canSetValue();
      }
    }
    e.getPresentation().setVisible(enable);
  }

  private void update(final DebuggerContextImpl context) {
    DebuggerInvocationUtil.invokeLater(context.getProject(), new Runnable() {
      public void run() {
        context.getDebuggerSession().refresh();
      }
    });
    //node.setState(context);
  }

  public void actionPerformed(AnActionEvent event) {
    final DebuggerTreeNodeImpl node = getSelectedNode(event.getDataContext());
    if (node == null) return;
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptorImpl)) return;
    if(!((ValueDescriptorImpl)descriptor).canSetValue()) return;

    DebuggerTree tree = getTree(event.getDataContext());
    final DebuggerContextImpl debuggerContext = getDebuggerContext(event.getDataContext());
    tree.saveState(node);

    if (descriptor instanceof FieldDescriptorImpl) {
      FieldDescriptorImpl fieldDescriptor = (FieldDescriptorImpl)descriptor;
      final Field field = fieldDescriptor.getField();
      if (!field.isStatic()) {
        final ObjectReference object = fieldDescriptor.getObject();
        if (object != null) {
          askAndSet(node, debuggerContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
              object.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
              update(debuggerContext);
            }

            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                         ClassNotLoadedException,
                                                                                                         IncompatibleThreadStateException,
                                                                                                         InvalidTypeException,
                                                                                                         EvaluateException {
              return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                   field.declaringType().classLoader());
            }
          });
        }
      }
      else {
        // field is static
        ReferenceType refType = field.declaringType();
        if (refType instanceof ClassType) {
          final ClassType classType = (ClassType)refType;
          askAndSet(node, debuggerContext, new SetValueRunnable() {
            public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
              classType.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
              update(debuggerContext);
            }

            public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                         ClassNotLoadedException,
                                                                                                         IncompatibleThreadStateException,
                                                                                                         InvalidTypeException,
                                                                                                         EvaluateException {
              return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                   field.declaringType().classLoader());
            }
          });
        }
      }
    }
    else if (descriptor instanceof LocalVariableDescriptorImpl) {
      LocalVariableDescriptorImpl localDescriptor = (LocalVariableDescriptorImpl)descriptor;
      final LocalVariableProxyImpl local = localDescriptor.getLocalVariable();
      if (local != null) {
        askAndSet(node, debuggerContext, new SetValueRunnable() {
          public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                           InvalidTypeException,
                                                                                           EvaluateException,
                                                                                           IncompatibleThreadStateException {
            debuggerContext.getFrameProxy().setValue(local, preprocessValue(evaluationContext, newValue, local.getVariable().type()));
            update(debuggerContext);
          }

          public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                       ClassNotLoadedException,
                                                                                                       IncompatibleThreadStateException,
                                                                                                       InvalidTypeException,
                                                                                                       EvaluateException {
            return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                 evaluationContext.getClassLoader());
          }
        });
      }
    }
    else if (descriptor instanceof ArrayElementDescriptorImpl) {
      final ArrayElementDescriptorImpl elementDescriptor = (ArrayElementDescriptorImpl)descriptor;
      final ArrayReference array = elementDescriptor.getArray();
      if (array != null) {
        final ArrayType arrType = (ArrayType)array.referenceType();
        askAndSet(node, debuggerContext, new SetValueRunnable() {
          public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
            array.setValue(elementDescriptor.getIndex(), preprocessValue(evaluationContext, newValue, arrType.componentType()));
            update(debuggerContext);
          }

          public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                       ClassNotLoadedException,
                                                                                                       IncompatibleThreadStateException,
                                                                                                       InvalidTypeException,
                                                                                                       EvaluateException {
            return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, arrType.classLoader());
          }
        });
      }
    }
    else if (descriptor instanceof EvaluationDescriptor) {
      final EvaluationDescriptor evaluationDescriptor = (EvaluationDescriptor)descriptor;
      if (evaluationDescriptor.canSetValue()) {
        askAndSet(node, debuggerContext, new SetValueRunnable() {
          public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException {
            final Modifier modifier = evaluationDescriptor.getModifier();
            modifier.setValue(preprocessValue(evaluationContext, newValue, modifier.getExpectedType()));
            update(debuggerContext);
          }

          public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                       ClassNotLoadedException,
                                                                                                       IncompatibleThreadStateException,
                                                                                                       InvalidTypeException,
                                                                                                       EvaluateException {
            return evaluationContext.getDebugProcess().loadClass(evaluationContext, className,
                                                                 evaluationContext.getClassLoader());
          }
        });
      }
    }
  }

  private Value preprocessValue(EvaluationContextImpl context, Value value, Type varType) throws EvaluateException {
    if (value != null && "java.lang.String".equals(varType.name()) && !(value instanceof StringReference)) {
      String v = DebuggerUtilsEx.getValueAsString(context, value);
      if (v != null) {
        value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf(v);
      }
    }
    if(value instanceof DoubleValue) {
      double dValue = ((DoubleValue) value).doubleValue();
      if(varType instanceof FloatType && Float.MIN_VALUE <= dValue && dValue <= Float.MAX_VALUE){
        value = context.getSuspendContext().getDebugProcess().getVirtualMachineProxy().mirrorOf((float)dValue);
      }
    }
    return value;
  }

  private static interface SetValueRunnable {
    void          setValue   (EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                          InvalidTypeException,
                                                                                          EvaluateException,
                                                                                          IncompatibleThreadStateException;
    ReferenceType loadClass  (EvaluationContextImpl evaluationContext, String className) throws EvaluateException,
                                                                                            InvocationException,
                                                                                            ClassNotLoadedException,
                                                                                            IncompatibleThreadStateException,
                                                                                            InvalidTypeException;
  }

  private static void setValue(String expressionToShow, ExpressionEvaluator evaluator, EvaluationContextImpl evaluationContext, SetValueRunnable setValueRunnable) throws EvaluateException {
    Value value;
    try {
      value = evaluator.evaluate(evaluationContext);

      setValueRunnable.setValue(evaluationContext, value);
    } catch (EvaluateException e1) {
      throw EvaluateExceptionUtil.createEvaluateException("Failed to evaluate expression '"+
          expressionToShow +  "'. \n" + e1.getMessage());
    }
    catch (IllegalArgumentException ex) {
      throw EvaluateExceptionUtil.createEvaluateException("Failed to evaluate expression. '" +
          expressionToShow + "'. \n" + "Invalid arguments :" + ex.getMessage());
    }
    catch (InvalidTypeException ex) {
      throw EvaluateExceptionUtil.createEvaluateException("Failed to set value : type mismatch");
    }
    catch (IncompatibleThreadStateException e) {
      throw EvaluateExceptionUtil.createEvaluateException(e);
    }
    catch (ClassNotLoadedException ex) {
      final ReferenceType refType;
      try {
        refType = setValueRunnable.loadClass(evaluationContext, ex.className());
        if (refType != null) {
          //try again
          setValue(expressionToShow, evaluator, evaluationContext, setValueRunnable);
        }
      }
      catch (InvocationException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ClassNotLoadedException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (IncompatibleThreadStateException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (InvalidTypeException e) {
        throw EvaluateExceptionUtil.createEvaluateException(e);
      }
      catch (ObjectCollectedException e) {
        throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
      }
    }
  }

  private void askAndSet(final DebuggerTreeNodeImpl node, final DebuggerContextImpl debuggerContext, final SetValueRunnable setValueRunnable) {
    ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, debuggerContext.getProject());

    SuspendContextCommandImpl askSetAction = new DebuggerContextCommandImpl(debuggerContext) {
      public void threadAction() {
        final NodeDescriptorImpl descriptor = node.getDescriptor();
        String initialString = "";
        if (descriptor instanceof ValueDescriptorImpl) {
          Value currentValue = ((ValueDescriptorImpl) descriptor).getValue();
          if (currentValue instanceof StringReference) {
            initialString = DebuggerUtilsEx.getValueOrErrorAsString(debuggerContext.createEvaluationContext(), currentValue);
            initialString = initialString == null ? "" : "\"" + DebuggerUtilsEx.translateStringValue(initialString) + "\"";
          }
          else if (currentValue instanceof PrimitiveValue) {
            ValueLabelRenderer renderer = ((ValueDescriptorImpl) descriptor).getRenderer(debuggerContext.getDebugProcess());
            initialString = getDisplayableString((PrimitiveValue) currentValue, renderer instanceof NodeRenderer && "HexRenderer".equals(((NodeRenderer) renderer).getUniqueId()));
          }

          final String initialString1 = initialString;
          DebuggerInvocationUtil.invokeLater(debuggerContext.getProject(), new Runnable() {
            public void run() {
              showEditor(TextWithImportsImpl.createExpressionText(initialString1), node, debuggerContext, setValueRunnable);
            }
          });
        }
      }
    };

    progressWindow.setTitle("Evaluating...");
    debuggerContext.getDebugProcess().getManagerThread().startProgress(askSetAction, progressWindow);
  }

  private void showEditor(final TextWithImportsImpl initialString,
                          final DebuggerTreeNodeImpl node,
                          final DebuggerContextImpl debuggerContext,
                          final SetValueRunnable setValueRunnable) {
    final JPanel editorPanel = new JPanel();
    editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));
    SimpleColoredComponent label = new SimpleColoredComponent();
    label.setIcon(node.getIcon());
    DebuggerTreeRenderer.getDescriptorTitle(node.getDescriptor()).appendToComponent(label);
    editorPanel.add(label);

    final DebuggerExpressionComboBox comboBox = new DebuggerExpressionComboBox(
              debuggerContext.getProject(),
              PositionUtil.getContextElement(debuggerContext),
              "setValue");
    comboBox.setText(initialString);
    editorPanel.add(comboBox);

    final DebuggerTree.InplaceEditor editor = new DebuggerTree.InplaceEditor(node) {
      public JComponent createEditorComponent() {
        return editorPanel;
      }

      public JComponent getContentComponent() {
        return comboBox;
      }

      public Editor getEditor() {
        return comboBox.getEditor();
      }

      private void setValue() {
        Editor editor = comboBox.getEditor();
        if(editor == null) return;

        final TextWithImportsImpl text = comboBox.getText();

        PsiFile psiFile = PsiDocumentManager.getInstance(debuggerContext.getProject()).getPsiFile(editor.getDocument());

        EditorEvaluationCommand evaluationCommand = new EditorEvaluationCommand(getEditor(), psiFile, debuggerContext) {
          public void threadAction() {
            try {
              evaluate();
            }
            catch(EvaluateException e) {
              getProgressWindow().cancel();
            }
            catch(ProcessCanceledException e) {
              getProgressWindow().cancel();
            }
            finally{
              if (!getProgressWindow().isCanceled()) {
                DebuggerInvocationUtil.invokeLater(debuggerContext.getProject(), new Runnable() {
                  public void run() {
                    comboBox.addRecent(text);
                    superDoCancelAction();
                  }
                });
              }
            }
          }

          protected Object evaluate(final EvaluationContextImpl evaluationContext) throws EvaluateException {
            ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(evaluationContext.getProject(), new com.intellij.debugger.EvaluatingComputable<ExpressionEvaluator>() {
              public ExpressionEvaluator compute() throws EvaluateException {
                return EvaluatorBuilderImpl.getInstance().build(text, ContextUtil.getContextElement(evaluationContext));
              }
            });

            SetValueAction.setValue(text.getText(), evaluator, evaluationContext, new SetValueRunnable() {
              public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException,
                                                                                               InvalidTypeException,
                                                                                               EvaluateException,
                                                                                               IncompatibleThreadStateException {
                if(!getProgressWindow().isCanceled()) {
                  setValueRunnable.setValue(evaluationContext, newValue);
                }
              }

              public ReferenceType loadClass(EvaluationContextImpl evaluationContext, String className) throws InvocationException,
                                                                                                           ClassNotLoadedException,
                                                                                                           EvaluateException,
                                                                                                           IncompatibleThreadStateException,
                                                                                                           InvalidTypeException {
                return setValueRunnable.loadClass(evaluationContext, className);
              }
            });

            return null;
          }
        };

        final ProgressWindowWithNotification progressWindow = evaluationCommand.getProgressWindow();
        progressWindow.addListener(new ProgressIndicatorListenerAdapter() {
          //should return whether to stop processing
          public void stopped() {
            if(!progressWindow.isCanceled()) {
              IJSwingUtilities.invoke(new Runnable() {
                public void run() {
                  superDoCancelAction();
                }
              });
            }
          }


        });

        progressWindow.setTitle("Setting value...");
        debuggerContext.getDebugProcess().getManagerThread().startProgress(evaluationCommand, progressWindow);
      }

      private void superDoCancelAction() {
        super.doCancelAction();
      }

      public void doOKAction() {
        setValue();
      }

      public void doFocusLostAction() {
        setValue();
      }
    };

    final DebuggerStateManager stateManager = DebuggerManagerEx.getInstanceEx(debuggerContext.getProject()).getContextManager();

    DebuggerContextListener listener = new DebuggerContextListener() {
          public void changeEvent(DebuggerContextImpl newContext, int event) {
            stateManager.removeListener(this);
            editor.doFocusLostAction();
          }
        };

    stateManager.addListener(listener);

    editor.show();
  }

  private static String getDisplayableString(PrimitiveValue value, boolean showAsHex) {
    if (value instanceof CharValue) {
      long longValue = value.longValue();
      return showAsHex ? "0x" + Long.toHexString(longValue).toUpperCase() : Long.toString(longValue);
    }
    if (value instanceof ByteValue) {
      byte val = value.byteValue();
      String strValue = Integer.toHexString(val).toUpperCase();
      if (strValue.length() > 2) {
        strValue = strValue.substring(strValue.length() - 2);
      }
      return showAsHex ? "0x" + strValue : value.toString();
    }
    if (value instanceof ShortValue) {
      short val = value.shortValue();
      String strValue = Integer.toHexString(val).toUpperCase();
      if (strValue.length() > 4) {
        strValue = strValue.substring(strValue.length() - 4);
      }
      return showAsHex ? "0x" + strValue : value.toString();
    }
    if (value instanceof IntegerValue) {
      int val = value.intValue();
      return showAsHex ? "0x" + Integer.toHexString(val).toUpperCase() : value.toString();
    }
    if (value instanceof LongValue) {
      long val = value.longValue();
      return showAsHex ? "0x" + Long.toHexString(val).toUpperCase() + "L" : value.toString() + "L";
    }
    return DebuggerUtilsEx.translateStringValue(value.toString());
  }

}
