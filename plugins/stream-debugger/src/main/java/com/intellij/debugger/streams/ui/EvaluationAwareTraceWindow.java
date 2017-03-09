package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedCall;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBTabsPaneImpl;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationAwareTraceWindow extends DialogWrapper {
  private final JComponent myCenterPane;
  private final List<MyTab> myTabContents;

  public EvaluationAwareTraceWindow(@Nullable Project project, @NotNull StreamChain chain) {
    super(project, false);
    final JBTabsPaneImpl tabs = new JBTabsPaneImpl(project, SwingConstants.TOP, getDisposable());
    setModal(false);
    setTitle("Stream Trace");
    myCenterPane = tabs.getComponent();
    myTabContents = new ArrayList<>();
    for (int i = 0, chainLength = chain.length(); i < chainLength; i++) {
      final StreamCall call = chain.getCall(i);
      final MyTab tab = new MyTab();
      tabs.insertTab(call.getName(), AllIcons.Debugger.Console, tab, call.getName() + call.getArguments(), i);
      myTabContents.add(tab);
    }
    init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow";
  }

  public void setTrace(@NotNull List<ResolvedCall> calls, @NotNull EvaluationContextImpl context) {
    assert calls.size() == myTabContents.size();
    for (int i = 0, count = calls.size(); i < count; i++) {
      final MyTab tab = myTabContents.get(i);
      final ResolvedCall call = calls.get(i);
      tab.setTrace(call, context);
    }
  }

  public void setFailMessage() {
    clear();
  }

  private void clear() {
    Arrays.stream(myCenterPane.getComponents()).forEach(myCenterPane::remove);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPane;
  }

  private static class MyTab extends JPanel {
    private static final JComponent EMPTY_CONTENT = new JBLabel("Evaluation in process", SwingConstants.CENTER);

    MyTab() {
      super(new BorderLayout());
      add(EMPTY_CONTENT, BorderLayout.CENTER);
    }

    void setTrace(@NotNull ResolvedCall call, @NotNull EvaluationContextImpl context) {
      Arrays.stream(getComponents()).forEach(this::remove);
      add(new CollectionView(context, call), BorderLayout.CENTER);
      revalidate();
      repaint();
    }
  }
}
