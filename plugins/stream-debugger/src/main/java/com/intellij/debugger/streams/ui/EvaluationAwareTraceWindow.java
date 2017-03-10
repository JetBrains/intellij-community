package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBTabsPaneImpl;
import com.intellij.ui.components.JBLabel;
import com.sun.jdi.Value;
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

  public void setTrace(@NotNull List<ResolvedTrace> traces, @Nullable Value result, @NotNull EvaluationContextImpl context) {
    assert myTabContents.size() == traces.size() + 1;
    final List<CollectionView> views = new ArrayList<>();
    for (final ResolvedTrace trace : traces) {
      final CollectionView view = new CollectionView(context, trace);
      Disposer.register(myDisposable, view);
      views.add(view);
    }

    for (int i = 1; i < views.size(); i++) {
      final CollectionView prev = views.get(i - 1);
      final CollectionView current = views.get(i);

      prev.setForwardListener(current);
      current.setBackwardListener(prev);

      final MyTab tab = myTabContents.get(i);
      final JPanel panel = new JPanel(new GridLayout(1, 2));
      panel.add(prev, 0);
      panel.add(current, 1);
      tab.setContent(panel);
    }

    //myTabContents.get(0).setContent(views.get(0));
    final MyTab resultTab = myTabContents.get(myTabContents.size() - 1);
    if (result != null) {
      resultTab.setContent(new JBLabel("Reserved for result!"));
    }
    else {
      resultTab.setContent(new JBLabel("There is no result of this stream chain"));
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

    void setContent(@NotNull JComponent view) {
      Arrays.stream(getComponents()).forEach(this::remove);
      add(view);
      revalidate();
      repaint();
    }
  }
}
