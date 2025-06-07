// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.ui.impl;

import com.intellij.CommonBundle;
import com.intellij.debugger.streams.core.StreamDebuggerBundle;
import com.intellij.debugger.streams.core.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.core.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.core.trace.*;
import com.intellij.debugger.streams.core.ui.TraceController;
import com.intellij.debugger.streams.core.wrapper.QualifierExpression;
import com.intellij.debugger.streams.core.wrapper.StreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.core.wrapper.TraceUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBTabsPaneImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBDimension;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class EvaluationAwareTraceWindow extends DialogWrapper {
  private static final String IS_FLAT_MODE_PROPERTY = "org.jetbrains.debugger.streams:isTraceWindowInFlatMode";
  private static final boolean IS_DEFAULT_MODE_FLAT = false;

  private static final int DEFAULT_WIDTH = 870;
  private static final int DEFAULT_HEIGHT = 400;
  private final MyCenterPane myCenterPane;
  private final List<MyPlaceholder> myTabContents;
  private final MyPlaceholder myFlatContent;
  private final JBTabsPaneImpl myTabsPane;

  private MyMode myMode;

  public EvaluationAwareTraceWindow(@NotNull XDebugSession session, @NotNull StreamChain chain) {
    super(session.getProject(), true);
    myTabsPane = new JBTabsPaneImpl(session.getProject(), SwingConstants.TOP, getDisposable());
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().invokeLater(() -> close(CLOSE_EXIT_CODE));
      }
    }, myDisposable);
    setModal(false);
    setTitle(StreamDebuggerBundle.message("stream.debugger.dialog.title"));
    myCenterPane = new MyCenterPane();
    myCenterPane.add(MyMode.SPLIT.name(), myTabsPane.getComponent());

    myTabContents = new ArrayList<>();
    final QualifierExpression qualifierExpression = chain.getQualifierExpression();
    final MyPlaceholder firstTab = new MyPlaceholder();
    myTabsPane.insertTab(TraceUtil.formatQualifierExpression(qualifierExpression.getText(), 30),
                         AllIcons.Debugger.Console, firstTab, qualifierExpression.getText(), 0);
    myTabContents.add(firstTab);

    for (int i = 0, chainLength = chain.length(); i < chainLength; i++) {
      final StreamCall call = chain.getCall(i);
      final MyPlaceholder tab = new MyPlaceholder();
      final String tabTitle = call.getTabTitle();
      final String tabTooltip = call.getTabTooltip();
      myTabsPane.insertTab(tabTitle, AllIcons.Debugger.Console, tab, tabTooltip, i + 1);
      myTabContents.add(tab);
    }

    myFlatContent = new MyPlaceholder();
    myCenterPane.add(MyMode.FLAT.name(), myFlatContent);
    myCenterPane.setPreferredSize(new JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));

    if (!PropertiesComponent.getInstance().isValueSet(IS_FLAT_MODE_PROPERTY)) {
      PropertiesComponent.getInstance().setValue(IS_FLAT_MODE_PROPERTY, IS_DEFAULT_MODE_FLAT);
    }

    myMode = PropertiesComponent.getInstance().getBoolean(IS_FLAT_MODE_PROPERTY) ? MyMode.FLAT : MyMode.SPLIT;
    updateWindowMode(myCenterPane, myMode);

    init();
  }

  @Override
  protected @Nullable String getDimensionServiceKey() {
    return "#com.intellij.debugger.streams.ui.EvaluationAwareTraceWindow";
  }

  public void setTrace(@NotNull ResolvedTracingResult resolvedTrace, @NotNull DebuggerCommandLauncher launcher, @NotNull GenericEvaluationContext context,
                       @NotNull CollectionTreeBuilder builder) {
    if (Disposer.isDisposed(myDisposable)) {
      return;
    }

    final ResolvedStreamChain chain = resolvedTrace.getResolvedChain();

    assert chain.length() == myTabContents.size();
    final List<TraceControllerImpl> controllers = createControllers(resolvedTrace);

    if (controllers.isEmpty()) return;
    final List<TraceElement> trace = controllers.get(0).getTrace();
    final CollectionTree tree = CollectionTree.create(controllers.get(0).getStreamResult(), trace, launcher, context, builder, "setTrace#Tree#0#");
    final CollectionView sourceView = new CollectionView(tree);
    controllers.get(0).register(sourceView);
    myTabContents.get(0).setContent(sourceView, BorderLayout.CENTER);

    for (int i = 1; i < myTabContents.size(); i++) {
      if (i == myTabContents.size() - 1 &&
          (resolvedTrace.exceptionThrown() ||
           resolvedTrace.getSourceChain().getTerminationCall().returnsVoid())) {
        break;
      }

      final MyPlaceholder tab = myTabContents.get(i);
      final TraceController previous = controllers.get(i - 1);
      final TraceController current = controllers.get(i);

      final StreamTracesMappingView
        view = new StreamTracesMappingView(launcher, context, previous, current, builder, "setTrace#MappingView#" + i + "#");
      tab.setContent(view, BorderLayout.CENTER);
    }

    final TraceElement result = resolvedTrace.getResult();
    final MyPlaceholder resultTab = myTabContents.get(myTabContents.size() - 1);

    if (resolvedTrace.exceptionThrown()) {
      JBLabel label = new JBLabel(StreamDebuggerBundle.message("tab.content.exception.thrown"), SwingConstants.CENTER);
      resultTab.setContent(label, BorderLayout.CENTER);
      setTitle(StreamDebuggerBundle.message("stream.debugger.dialog.with.exception.title"));
      final ExceptionView exceptionView = new ExceptionView(launcher, context, result, builder);
      Disposer.register(myDisposable, exceptionView);
      myTabsPane.insertTab(StreamDebuggerBundle.message("exception.tab.name"), AllIcons.Nodes.ErrorIntroduction, exceptionView, "", 0);
      myTabsPane.setSelectedIndex(0);
    }
    else if (resolvedTrace.getSourceChain().getTerminationCall().returnsVoid()) {
      JBLabel label = new JBLabel(StreamDebuggerBundle.message("tab.content.no.result"), SwingConstants.CENTER);
      resultTab.setContent(label, BorderLayout.CENTER);
    }

    final FlatView flatView = new FlatView(controllers, launcher, context, builder, "setTrace#FlatView#");
    myFlatContent.setContent(flatView, BorderLayout.CENTER);
    myCenterPane.revalidate();
    myCenterPane.repaint();
  }

  public void setFailMessage(@NotNull @Nls String reason) {
    StreamEx.of(myTabContents).prepend(myFlatContent)
            .forEach(x -> x.setContent(new JBLabel(reason, SwingConstants.CENTER), BorderLayout.CENTER));
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{new DialogWrapperExitAction(CommonBundle.message("action.text.close"), CLOSE_EXIT_CODE)};
  }

  @Override
  protected Action @NotNull [] createLeftSideActions() {
    return new Action[]{new MyToggleViewAction()};
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myCenterPane;
  }

  private @NotNull List<TraceControllerImpl> createControllers(@NotNull ResolvedTracingResult resolvedResult) {
    List<TraceControllerImpl> controllers = new ArrayList<>();
    final ResolvedStreamChain chain = resolvedResult.getResolvedChain();

    final List<ResolvedStreamCall.Intermediate> intermediateCalls = chain.getIntermediateCalls();
    final NextAwareState firstState = intermediateCalls.isEmpty()
                                      ? chain.getTerminator().getStateBefore()
                                      : intermediateCalls.get(0).getStateBefore();
    final TraceControllerImpl firstController = new TraceControllerImpl(firstState);

    controllers.add(firstController);
    TraceControllerImpl prevController = firstController;
    for (final ResolvedStreamCall.Intermediate intermediate : intermediateCalls) {
      final PrevAwareState after = intermediate.getStateAfter();
      final TraceControllerImpl controller = new TraceControllerImpl(after);

      prevController.setNextController(controller);
      controller.setPreviousController(prevController);
      prevController = controller;

      controllers.add(controller);
    }

    final ResolvedStreamCall.Terminator terminator = chain.getTerminator();
    final IntermediateState afterTerminationState = terminator.getStateAfter();
    if (afterTerminationState != null && !terminator.getCall().returnsVoid()) {

      final TraceControllerImpl terminationController = new TraceControllerImpl(afterTerminationState);

      terminationController.setPreviousController(prevController);
      prevController.setNextController(terminationController);
      controllers.add(terminationController);
    }

    controllers.forEach(x -> Disposer.register(myDisposable, x));
    return controllers;
  }

  private static void updateWindowMode(@NotNull MyCenterPane pane, @NotNull MyMode mode) {
    pane.getLayout().show(pane, mode.name());
    PropertiesComponent.getInstance().setValue(IS_FLAT_MODE_PROPERTY, MyMode.FLAT.equals(mode));
  }

  private static @NotNull @Nls String getButtonText(@NotNull MyMode currentState) {
    return MyMode.SPLIT.equals(currentState)
           ? StreamDebuggerBundle.message("stream.debugger.dialog.flat.mode.button")
           : StreamDebuggerBundle.message("stream.debugger.dialog.split.mode.button");
  }

  private class MyToggleViewAction extends DialogWrapperAction {
    MyToggleViewAction() {
      super(getButtonText(myMode));
    }

    @Override
    protected void doAction(ActionEvent e) {
      final JButton button = getButton(this);
      if (button != null) {
        myMode = toggleMode(myMode);
        button.setText(getButtonText(myMode));
      }

      updateWindowMode(myCenterPane, myMode);
    }

    private static @NotNull MyMode toggleMode(@NotNull MyMode mode) {
      return MyMode.FLAT.equals(mode) ? MyMode.SPLIT : MyMode.FLAT;
    }
  }

  private static class MyPlaceholder extends JPanel {
    MyPlaceholder() {
      super(new BorderLayout());
      add(new JBLabel(StreamDebuggerBundle.message("evaluation.in.progress"), SwingConstants.CENTER), BorderLayout.CENTER);
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

  private static class MyCenterPane extends JPanel {
    MyCenterPane() {
      super(new JBCardLayout());
    }

    @Override
    public JBCardLayout getLayout() {
      return (JBCardLayout)super.getLayout();
    }
  }
}
