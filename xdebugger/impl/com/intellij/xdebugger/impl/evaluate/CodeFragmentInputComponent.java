package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerMultilineEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class CodeFragmentInputComponent extends EvaluationInputComponent {
  private final XDebuggerMultilineEditor myMultilineEditor;

  public CodeFragmentInputComponent(final @NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider, final @Nullable XSourcePosition sourcePosition,
                                  @Nullable String statements) {
    super(XDebuggerBundle.message("dialog.title.evaluate.code.fragment"));
    myMultilineEditor = new XDebuggerMultilineEditor(project, editorsProvider, "evaluateCodeBlock", sourcePosition, statements != null ? statements : "");
  }

  protected XDebuggerEditorBase getInputEditor() {
    return myMultilineEditor;
  }

  public JComponent getComponent() {
    return myMultilineEditor.getComponent();
  }
}
