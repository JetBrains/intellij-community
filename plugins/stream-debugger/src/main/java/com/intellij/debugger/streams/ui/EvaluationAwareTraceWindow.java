package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationAwareTraceWindow extends DialogWrapper {
  private static final JComponent EMPTY_CONTENT = new JBLabel("Evaluation in process", SwingConstants.CENTER);
  private static final JComponent FAIL_CONTENT = new JBLabel("Tracing failed", SwingConstants.CENTER);

  private final JPanel myCenterPane;

  public EvaluationAwareTraceWindow(@Nullable Project project, @NotNull StreamChain chain) {
    super(project, false);
    setModal(false);
    setTitle("Stream Trace");
    myCenterPane = new JBPanel(new GridLayout(1, chain.length()));
    for (int i = 0, chainLength = chain.length(); i < chainLength; i++) {
      //myCenterPane.add();
    }
    init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow";
  }

  public void setTrace(@NotNull List<ResolvedCall> calls, @NotNull EvaluationContextImpl context) {
    clear();
    myCenterPane.add(new JBLabel("Done!", SwingConstants.CENTER));
    myCenterPane.revalidate();
    myCenterPane.repaint();
  }

  public void setFailMessage() {
    clear();
    myCenterPane.add(FAIL_CONTENT);
  }

  private void clear() {
    Arrays.stream(myCenterPane.getComponents()).forEach(myCenterPane::remove);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPane;
  }
}
