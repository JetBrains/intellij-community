/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.ui.impl;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.streams.resolve.ResolvedTrace;
import com.intellij.debugger.streams.resolve.ResolvedTraceImpl;
import com.intellij.debugger.streams.trace.ResolvedTracingResult;
import com.intellij.debugger.streams.trace.impl.TraceElementImpl;
import com.intellij.debugger.streams.ui.TraceController;
import com.intellij.debugger.streams.wrapper.StreamCall;
import com.intellij.debugger.streams.wrapper.StreamChain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBTabsPaneImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.sun.jdi.Value;
import icons.StreamDebuggerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationAwareTraceWindow extends DialogWrapper {
  private static final int DEFAULT_WIDTH = 870;
  private static final int DEFAULT_HEIGHT = 400;
  private static final String FLAT_MODE_NAME = "Flat Mode";
  private static final String TABBED_MODE_NAME = "Split Mode";
  private final JPanel myCenterPane;
  private final List<MyPlaceholder> myTabContents;
  private final MyPlaceholder myFlatContent;
  private final StreamChain myStreamChain;

  private MyMode myMode = MyMode.SPLIT;

  public EvaluationAwareTraceWindow(@NotNull XDebugSession session, @NotNull StreamChain chain) {
    super(session.getProject(), true);
    final JBTabsPaneImpl tabs = new JBTabsPaneImpl(session.getProject(), SwingConstants.TOP, getDisposable());
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().invokeLater(() -> close(CLOSE_EXIT_CODE));
      }
    }, myDisposable);
    setModal(false);
    setTitle("Stream Trace");
    myStreamChain = chain;
    final JBCardLayout layout = new JBCardLayout();
    myCenterPane = new JPanel(layout);
    myCenterPane.add(tabs.getComponent());
    myTabContents = new ArrayList<>();
    for (int i = 0, chainLength = chain.length(); i < chainLength; i++) {
      final StreamCall call = chain.getCall(i);
      final MyPlaceholder tab = new MyPlaceholder();
      tabs.insertTab(call.getName(), StreamDebuggerIcons.STREAM_CALL_TAB_ICON, tab, call.getName() + call.getArguments(), i);
      myTabContents.add(tab);
    }

    myFlatContent = new MyPlaceholder();
    myCenterPane.add(myFlatContent);
    myCenterPane.setPreferredSize(new JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));

    init();
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow";
  }

  public void setTrace(@NotNull ResolvedTracingResult resolvedTrace, @NotNull EvaluationContextImpl context) {
    final List<ResolvedTrace> traces = resolvedTrace.getResolvedTraces();
    assert myTabContents.size() == traces.size() + 1;

    List<TraceController> controllers = new ArrayList<>();
    TraceControllerImpl prev = null;
    for (final ResolvedTrace trace : traces) {
      final TraceControllerImpl current = new TraceControllerImpl(trace);
      if (prev != null) {
        current.setPreviousListener(prev);
        prev.setNextListener(current);
      }

      controllers.add(current);
      prev = current;
    }

    final CollectionView sourceView = new CollectionView("Source", context, traces.get(0).getValues());
    controllers.get(0).register(sourceView);
    myTabContents.get(0).setContent(sourceView, BorderLayout.CENTER);

    for (int i = 1; i < myTabContents.size() - 1; i++) {
      final MyPlaceholder tab = myTabContents.get(i);
      final TraceController previous = controllers.get(i - 1);
      final TraceController current = controllers.get(i);

      final StreamTracesMappingView view = new StreamTracesMappingView(context, previous, current);
      tab.setContent(view, BorderLayout.CENTER);
    }

    final MyPlaceholder resultTab = myTabContents.get(myTabContents.size() - 1);
    final Value result = resolvedTrace.getResult();
    if (result != null) {
      final TraceElementImpl resultTraceElement = new TraceElementImpl(Integer.MAX_VALUE, result);
      final CollectionView view = new CollectionView("Result", context, Collections.singletonList(resultTraceElement));
      resultTab.setContent(view, BorderLayout.CENTER);
      final ResolvedTraceImpl terminationTrace = new ResolvedTraceImpl(myStreamChain.getTerminationCall(),
                                                                       Collections.singletonList(resultTraceElement),
                                                                       Collections.emptyMap(),
                                                                       Collections.emptyMap());
      controllers.add(new TraceControllerImpl(terminationTrace));
    }
    else {
      resultTab.setContent(new JBLabel("There is no result of such stream chain", SwingConstants.CENTER), BorderLayout.CENTER);
    }


    final FlatView flatView = new FlatView(controllers, context);
    myFlatContent.setContent(flatView, BorderLayout.CENTER);
    final JComponent panel = getContentPanel();
    panel.revalidate();
    panel.repaint();
  }

  public void setFailMessage(@NotNull String reason) {
    Stream.concat(Stream.of(myFlatContent), myTabContents.stream())
      .forEach(x -> x.setContent(new JBLabel(reason, SwingConstants.CENTER), BorderLayout.CENTER));
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{new DialogWrapperExitAction("Close", CLOSE_EXIT_CODE)};
  }

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[]{new MyToggleViewAction()};
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPane;
  }

  private class MyToggleViewAction extends DialogWrapperAction {
    MyToggleViewAction() {
      super(FLAT_MODE_NAME);
    }

    @Override
    protected void doAction(ActionEvent e) {
      final JButton button = getButton(this);
      if (button != null) {
        myMode = toggleMode(myMode);
        button.setText(getButtonText(myMode));
      }

      ((JBCardLayout)myCenterPane.getLayout()).next(myCenterPane);
    }

    @NotNull
    private String getButtonText(@NotNull MyMode mode) {
      return MyMode.SPLIT.equals(mode) ? FLAT_MODE_NAME : TABBED_MODE_NAME;
    }

    @NotNull
    private MyMode toggleMode(@NotNull MyMode mode) {
      return MyMode.FLAT.equals(mode) ? MyMode.SPLIT : MyMode.FLAT;
    }
  }

  private static class MyPlaceholder extends JPanel {
    MyPlaceholder() {
      super(new BorderLayout());
      add(new JBLabel("Evaluation in process", SwingConstants.CENTER), BorderLayout.CENTER);
    }

    void setContent(@NotNull JComponent view, String placement) {
      Arrays.stream(getComponents()).forEach(this::remove);
      add(view, placement);
      revalidate();
      repaint();
    }
  }

  private enum MyMode {
    FLAT, SPLIT
  }
}
