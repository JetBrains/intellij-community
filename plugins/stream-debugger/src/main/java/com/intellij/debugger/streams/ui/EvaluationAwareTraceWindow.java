package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.trace.TracingResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationAwareTraceWindow extends DialogWrapper {
  private static final JComponent EMPTY_CONTENT = new JBLabel("Evaluation in process", SwingConstants.CENTER);
  private static final JComponent FAIL_CONTENT = new JBLabel("Tracing failed", SwingConstants.CENTER);

  private final JPanel myCenterPane;

  public EvaluationAwareTraceWindow(@Nullable Project project) {
    super(project, false);
    setModal(false);
    setTitle("Stream Trace");
    myCenterPane = new BorderLayoutPanel();
    myCenterPane.add(EMPTY_CONTENT);
    myCenterPane.setPreferredSize(new JBDimension(100, 100));
    init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow";
  }

  public void setTrace(@NotNull TracingResult result, @NotNull EvaluationContextImpl context) {
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
