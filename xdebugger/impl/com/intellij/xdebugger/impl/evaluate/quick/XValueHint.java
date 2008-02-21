package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.evaluate.quick.XValueHint");
  private final XDebuggerEvaluator myEvaluator;
  private final XDebugSession myDebugSession;
  private String myExpression;

  public XValueHint(final Project project, final Editor editor, final Point point, final int type, final TextRange textRange,
                    final XDebuggerEvaluator evaluator, final XDebugSession session) {
    super(project, editor, point, type, textRange);
    myEvaluator = evaluator;
    myDebugSession = session;
    myExpression = textRange.substring(editor.getDocument().getText());
  }


  protected boolean canShowHint() {
    return true;
  }

  protected void evaluateAndShowHint() {
    myEvaluator.evaluate(myExpression, new XDebuggerEvaluator.XEvaluationCallback() {
      public void evaluated(@NotNull final XValue result) {
        result.computePresentation(new XValueNode() {
          public void setPresentation(@NonNls @NotNull final String name, @Nullable final Icon icon, @NonNls @Nullable final String type, @NonNls @NotNull final String value,
                                      final boolean hasChildren) {
            DebuggerUIUtil.invokeLater(new Runnable() {
              public void run() {
                doShowHint(result, name, value, hasChildren);
              }
            });
          }
        });
      }

      public void errorOccured(@NotNull final String errorMessage) {
        LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
      }
    });
  }

  private void doShowHint(final XValue xValue, final String name, final String value, final boolean hasChildren) {
    if (isHintHidden()) return;

    SimpleColoredText text = new SimpleColoredText();
    text.append(name, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    text.append(XDebuggerUIConstants.EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    text.append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    
    JComponent component;
    if (!hasChildren) {
      component = HintUtil.createInformationLabel(text);
    }
    else {
      component = createExpandableHintComponent(text, new Runnable() {
        public void run() {
          showTree(xValue, name);
        }
      });
    }
    showHint(component);
  }

  private void showTree(final XValue value, final String name) {
    XDebuggerTree tree = new XDebuggerTree(getProject(), myDebugSession.getDebugProcess().getEditorsProvider(),
                                           myDebugSession.getCurrentPosition());
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    XValueHintTreeComponent component = new XValueHintTreeComponent(this, tree, Pair.create(value, name));
    showTreePopup(component, tree, name);
  }
}
