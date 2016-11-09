package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.CachedEvaluator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.*;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.min;

public class InstancesWindow extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(InstancesWindow.class);

  private static final int DEFAULT_WINDOW_WIDTH = 870;
  private static final int DEFAULT_WINDOW_HEIGHT = 400;
  private static final int FILTERING_BUTTON_ADDITIONAL_WIDTH = 30;
  private static final int BORDER_LAYOUT_DEFAULT_GAP = 5;
  private static final int DEFAULT_INSTANCES_LIMIT = 500000; // All

  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final InstancesProvider myInstancesProvider;
  private final String myClassName;
  private MyInstancesView myInstancesView;

  public InstancesWindow(@NotNull XDebugSession session,
                         @NotNull InstancesProvider provider,
                         @NotNull String className) {
    super(session.getProject(), false);

    myProject = session.getProject();
    myDebugSession = session;
    myInstancesProvider = provider;
    myClassName = className;

    addWarningMessage(null);
    myDebugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        SwingUtilities.invokeLater(() -> close(OK_EXIT_CODE));
      }
    }, myDisposable);
    setModal(false);
    init();
    JRootPane root = myInstancesView.getRootPane();
    root.setDefaultButton(myInstancesView.myFilterButton);
  }

  enum FilteringCompletionReason {
    ALL_CHECKED, INTERRUPTED, LIMIT_REACHED
  }

  private void addWarningMessage(@Nullable String message) {
    String warning = message == null ? "" : String.format(". Warning: %s", message);
    setTitle(String.format("Instances of %s%s", myClassName, warning));
  }

  @NotNull
  @Override
  protected String getDimensionServiceKey() {
    return "#org.jetbrains.debugger.memory.view.InstancesWindow";
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    myInstancesView = new MyInstancesView();
    myInstancesView.setPreferredSize(
        new JBDimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
    return myInstancesView;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent comp = super.createSouthPanel();
    if (comp != null) {
      comp.add(myInstancesView.myProgress, BorderLayout.WEST);
    }

    return comp;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{new DialogWrapperExitAction("Close", CLOSE_EXIT_CODE)};
  }

  private class MyInstancesView extends JBPanel implements Disposable {
    private static final int MAX_TREE_NODE_COUNT = 2000;
    private static final int FILTERING_CHUNK_SIZE = 30;

    private final InstancesTree myInstancesTree;
    private final XDebuggerExpressionEditor myFilterConditionEditor;
    private final XDebugSessionListener myDebugSessionListener = new MySessionListener();

    private final MyCachedEvaluator myEvaluator = new MyCachedEvaluator();
    private final MyNodeManager myNodeManager = new MyNodeManager(myProject);

    private final JButton myFilterButton = new JButton("Filter");
    private final FilteringProgressView myProgress = new FilteringProgressView();

    private final AnActionListener.Adapter myActionListener = new MyActionListener();
    private final Object myFilteringTaskLock = new Object();

    private boolean myIsAndroidVM = false;

    private volatile MyFilteringWorker myFilteringTask = null;

    MyInstancesView() {
      super(new BorderLayout(0, JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP)));

      Disposer.register(InstancesWindow.this.myDisposable, MyInstancesView.this);
      XValueMarkers<?, ?> markers = getValueMarkers();
      ActionManager.getInstance().addAnActionListener(myActionListener, InstancesWindow.this.myDisposable);
      myDebugSession.addSessionListener(myDebugSessionListener, InstancesWindow.this.myDisposable);
      JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();

      myFilterConditionEditor = new ExpressionEditorWithHistory(myProject, myClassName,
          editorsProvider, InstancesWindow.this.myDisposable);

      myFilterButton.setBorder(BorderFactory.createEmptyBorder());
      Dimension filteringButtonSize = myFilterConditionEditor.getEditorComponent().getPreferredSize();
      filteringButtonSize.width = JBUI.scale(FILTERING_BUTTON_ADDITIONAL_WIDTH) +
          myFilterButton.getPreferredSize().width;
      myFilterButton.setPreferredSize(filteringButtonSize);

      JBPanel filteringPane = new JBPanel(new BorderLayout(JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP), 0));
      JBLabel sideEffectsWarning = new JBLabel("Warning: filtering may have side effects", SwingConstants.RIGHT);
      sideEffectsWarning.setBorder(JBUI.Borders.empty(1, 0, 0, 0));
      sideEffectsWarning.setComponentStyle(UIUtil.ComponentStyle.SMALL);
      sideEffectsWarning.setFontColor(UIUtil.FontColor.BRIGHTER);

      filteringPane.add(new JBLabel("Condition:"), BorderLayout.WEST);
      filteringPane.add(myFilterConditionEditor.getComponent(), BorderLayout.CENTER);
      filteringPane.add(myFilterButton, BorderLayout.EAST);
      filteringPane.add(sideEffectsWarning, BorderLayout.SOUTH);

      myProgress.addStopActionListener(this::cancelFilteringTask);

      myInstancesTree = new InstancesTree(myProject, myDebugSession, editorsProvider, markers, this::updateInstances);

      myFilterButton.addActionListener(e -> {
        String expression = myFilterConditionEditor.getExpression().getExpression();
        if (!expression.isEmpty()) {
          myFilterConditionEditor.saveTextInHistory();
        }

        myFilterButton.setEnabled(false);
        myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES);
      });


      StackFrameList list = new StackFrameList(myProject,
          Collections.emptyList(),
          GlobalSearchScope.allScope(myProject));

      list.addListSelectionListener(e -> list.navigateToSelectedValue(false));
      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent event) {
          list.navigateToSelectedValue(true);
          return true;
        }
      }.installOn(list);

      InstancesWithStackFrameView instancesWithStackFrame = new InstancesWithStackFrameView(myDebugSession,
          myInstancesTree, list, myClassName);

      add(filteringPane, BorderLayout.NORTH);
      add(instancesWithStackFrame.getComponent(), BorderLayout.CENTER);

      JComponent focusedComponent = myFilterConditionEditor.getEditorComponent();
      UiNotifyConnector.doWhenFirstShown(focusedComponent, () ->
          IdeFocusManager.findInstanceByComponent(focusedComponent)
              .requestFocus(focusedComponent, true));
    }

    @Override
    public void dispose() {
      cancelFilteringTask();
      Disposer.dispose(myInstancesTree);
    }

    private void updateInstances() {
      DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject)
          .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());

      cancelFilteringTask();

      debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debugProcess.getDebuggerContext()) {
        @Override
        public Priority getPriority() {
          return Priority.LOWEST;
        }

        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          myIsAndroidVM = AndroidUtil.isAndroidVM(debugProcess.getVirtualMachineProxy().getVirtualMachine());
          int limit = myIsAndroidVM
              ? AndroidUtil.ANDROID_INSTANCES_LIMIT
              : DEFAULT_INSTANCES_LIMIT;
          List<ObjectReference> instances = myInstancesProvider.getInstances(limit + 1);

          EvaluationContextImpl evaluationContext = debugProcess
              .getDebuggerContext().createEvaluationContext();

          if (instances.size() > limit) {
            addWarningMessage(String.format("Not all instances will be loaded (only %d)", limit));
            instances = instances.subList(0, limit);
          }

          final int progressSize = instances.size();
          if (evaluationContext != null) {
            synchronized (myFilteringTaskLock) {
              myFilteringTask = new MyFilteringWorker(instances, evaluationContext, createEvaluator());
              SwingUtilities.invokeLater(() -> {
                myProgress.start(progressSize);
                myFilteringTask.execute();
              });
            }
          }
        }
      });
    }

    @Nullable
    private ExpressionEvaluator createEvaluator() {
      ExpressionEvaluator evaluator = null;

      XExpression expression = myFilterConditionEditor.getExpression();
      if (expression != null && !expression.getExpression().isEmpty()) {
        try {
          myEvaluator.setReferenceExpression(TextWithImportsImpl.fromXExpression(expression));
          evaluator = myEvaluator.getEvaluator();
        } catch (EvaluateException ignored) {
          LOG.info("cannot create evaluator by the expression = " + expression.getExpression());
        }
      }

      return evaluator;
    }

    private void cancelFilteringTask() {
      if (myFilteringTask != null) {
        synchronized (myFilteringTaskLock) {
          if (myFilteringTask != null) {
            myFilteringTask.cancel(true);
            myFilteringTask = null;
          }
        }
      }
    }

    private XValueMarkers<?, ?> getValueMarkers() {
      return myDebugSession instanceof XDebugSessionImpl
          ? ((XDebugSessionImpl) myDebugSession).getValueMarkers()
          : null;
    }

    private class MySessionListener implements XDebugSessionListener {
      private volatile XDebuggerTreeState myTreeState = null;

      @Override
      public void sessionResumed() {
        SwingUtilities.invokeLater(() -> {
          myTreeState = XDebuggerTreeState.saveState(myInstancesTree);
          cancelFilteringTask();

          myInstancesTree.setMessage(XDebuggerUIConstants.INFORMATION_MESSAGE_ICON,
              "The application is running", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        });
      }

      @Override
      public void sessionPaused() {
        SwingUtilities.invokeLater(() -> {
          myProgress.setVisible(true);
          myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES, myTreeState);
        });
      }
    }

    private class MyActionListener extends AnActionListener.Adapter {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) == myInstancesView.myInstancesTree &&
            (isAddToWatchesAction(action) || isEvaluateExpressionAction(action))) {
          XValueNodeImpl selectedNode = XDebuggerTreeActionBase.getSelectedNode(dataContext);
          XValueMarkers<?, ?> markers = getValueMarkers();

          if (markers != null && selectedNode != null) {

            TreeNode currentNode = selectedNode;
            while (!myInstancesTree.getRoot().equals(currentNode.getParent())) {
              currentNode = currentNode.getParent();
            }

            XValue valueContainer = ((XValueNodeImpl) currentNode).getValueContainer();

            String expression = valueContainer.getEvaluationExpression();
            if (expression != null) {
              markers.markValue(valueContainer,
                  new ValueMarkup(expression.replace("@", ""), new JBColor(0, 0), null));
            }

            SwingUtilities.invokeLater(() -> myInstancesTree
                .rebuildTree(InstancesTree.RebuildPolicy.ONLY_UPDATE_LABELS));
          }
        }
      }

      private boolean isAddToWatchesAction(AnAction action) {
        String className = action.getClass().getSimpleName();
        return action instanceof XDebuggerTreeActionBase && className.equals("XAddToWatchesAction");
      }

      private boolean isEvaluateExpressionAction(AnAction action) {
        String className = action.getClass().getSimpleName();
        return action instanceof XDebuggerActionBase && className.equals("EvaluateAction");
      }
    }

    private final class MyCachedEvaluator extends CachedEvaluator {
      @Override
      @NotNull
      protected String getClassName() {
        return myClassName;
      }

      ExpressionEvaluator getEvaluator() throws EvaluateException {
        return getEvaluator(myProject);
      }
    }

    private class MyFilteringWorker extends SwingWorker<Void, Void> {
      private final List<ObjectReference> myReferences;
      private final ErrorsValueGroup myErrorsGroup = new ErrorsValueGroup("Errors");
      private final ExpressionEvaluator myExpressionEvaluator;

      private final EvaluationContextImpl myEvaluationContext;
      private volatile boolean myDebuggerTaskCompleted = false;

      @NotNull
      private volatile FilteringCompletionReason myCompletionReason = FilteringCompletionReason.INTERRUPTED;

      MyFilteringWorker(@NotNull List<ObjectReference> refs,
                        @NotNull EvaluationContextImpl evaluationContext,
                        @Nullable ExpressionEvaluator evaluator) {
        myReferences = refs;
        myEvaluationContext = evaluationContext;
        myExpressionEvaluator = evaluator;
      }

      @Override
      protected void done() {
        if (!myErrorsGroup.isEmpty()) {
          XValueChildrenList lst = new XValueChildrenList();
          lst.addBottomGroup(myErrorsGroup);
          myInstancesTree.addChildren(lst, true);
        } else {
          myInstancesTree.addChildren(XValueChildrenList.EMPTY, true);
        }

        myFilterButton.setEnabled(true);
        SwingUtilities.invokeLater(() -> myProgress.complete(myCompletionReason));
      }

      @Override
      protected Void doInBackground() throws Exception {
        DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject)
            .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());

        AtomicInteger totalChildren = new AtomicInteger(0);
        AtomicInteger totalViewed = new AtomicInteger(0);
        long start = System.nanoTime();
        for (int i = 0, size = myReferences.size(); i < size && totalChildren.get() < MAX_TREE_NODE_COUNT;
             i += FILTERING_CHUNK_SIZE) {
          myDebuggerTaskCompleted = false;
          final int chunkBegin = i;
          debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debugProcess.getDebuggerContext()) {
            @Override
            public Priority getPriority() {
              return Priority.LOWEST;
            }

            @Override
            public void threadAction(@NotNull SuspendContextImpl suspendContext) {
              // TODO: need to rewrite this
              XValueChildrenList children = new XValueChildrenList();
              int endOfChunk = min(chunkBegin + FILTERING_CHUNK_SIZE, size);
              int errorCount = 0;
              for (int j = chunkBegin; j < endOfChunk && totalChildren.get() < MAX_TREE_NODE_COUNT; j++) {
                ObjectReference ref = myReferences.get(j);
                totalViewed.incrementAndGet();
                Pair<MyFilteringResult, String> comparison = null;
                if (myExpressionEvaluator != null) {
                  comparison = isSatisfy(myExpressionEvaluator, ref);
                  if (comparison.first == MyFilteringResult.EVAL_ERROR) {
                    errorCount++;
                  }

                  if (comparison.first == MyFilteringResult.NO_MATCH) {
                    continue;
                  }
                }

                JavaValue val = new InstanceJavaValue(null, new InstanceValueDescriptor(myProject, ref),
                    myEvaluationContext, myNodeManager, true);
                if (comparison == null || comparison.first == MyFilteringResult.MATCH) {
                  children.add(val);
                } else {
                  myErrorsGroup.addErrorValue(comparison.second, val);
                }

                totalChildren.incrementAndGet();
              }

              if (children.size() > 0) {
                SwingUtilities.invokeLater(() -> {
                  if (MyFilteringWorker.this == myFilteringTask) {
                    myInstancesTree.addChildren(children, false);
                  }
                });
              }

              final int childrenSize = children.size();
              final int finalErrorsCount = errorCount;
              SwingUtilities.invokeLater(() ->
                  myProgress.updateProgress(endOfChunk - chunkBegin, childrenSize, finalErrorsCount));

              synchronized (MyFilteringWorker.this) {
                myDebuggerTaskCompleted = true;
                MyFilteringWorker.this.notify();
              }
            }
          });

          synchronized (MyFilteringWorker.this) {
            while (!myDebuggerTaskCompleted) {
              MyFilteringWorker.this.wait();
            }
          }
        }

        LOG.info(String.format("Filtering time = %d ms, count = %d, matched = %d",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), totalViewed.get(), totalChildren.get()));

        myCompletionReason = totalViewed.get() == myReferences.size()
            ? FilteringCompletionReason.ALL_CHECKED
            : FilteringCompletionReason.LIMIT_REACHED;

        return null;
      }

      private Pair<MyFilteringResult, String> isSatisfy(@NotNull ExpressionEvaluator evaluator, @NotNull Value value) {
        try {
          Value result = evaluator.evaluate(myEvaluationContext.createEvaluationContext(value));
          if (result instanceof BooleanValue && ((BooleanValue) result).value()) {
            return Pair.create(MyFilteringResult.MATCH, "");
          }
        } catch (EvaluateException e) {
          return Pair.create(MyFilteringResult.EVAL_ERROR, e.getMessage());
        }

        return Pair.create(MyFilteringResult.NO_MATCH, "");
      }
    }
  }

  private final static class MyNodeManager extends NodeManagerImpl {
    MyNodeManager(Project project) {
      super(project, null);
    }

    @Override
    public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
      return new DebuggerTreeNodeImpl(null, descriptor);
    }

    @Override
    public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
      return new DebuggerTreeNodeImpl(null, descriptor);
    }

    @Override
    public DebuggerTreeNodeImpl createMessageNode(String message) {
      return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
    }
  }

  private enum MyFilteringResult {
    MATCH, NO_MATCH, EVAL_ERROR
  }
}
