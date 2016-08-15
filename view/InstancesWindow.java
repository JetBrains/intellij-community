package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBProgressBar;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.InstanceJavaValue;
import org.jetbrains.debugger.memory.utils.InstanceValueDescriptor;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.min;

public class InstancesWindow extends DialogWrapper {
  private static final int DEFAULT_WINDOW_WIDTH = 700;
  private static final int DEFAULT_WINDOW_HEIGHT = 400;
  private static final int FILTERING_BUTTON_ADDITIONAL_WIDTH = 30;
  private static final int BORDER_LAYOUT_DEFAULT_GAP = 5;

  private final Project myProject;
  private final XDebugSession myDebugSession;
  private final ReferenceType myReferenceType;
  private MyInstancesView myInstancesView;

  public InstancesWindow(@NotNull XDebugSession session,
                         @NotNull ReferenceType referenceType) {
    super(session.getProject(), false);

    myProject = session.getProject();
    myDebugSession = session;
    myReferenceType = referenceType;
    setTitle("Instances of " + referenceType.name());
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

  @NotNull
  @Override
  protected String getDimensionServiceKey() {
    return "#org.jetbrains.plugin.view.InstancesWindow";
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
      comp.add(myInstancesView.myProgressPanel, BorderLayout.WEST);
    }

    return comp;
  }

  @Override
  protected void dispose() {
    super.dispose();
    myInstancesView.dispose();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{new DialogWrapperExitAction("Close", CLOSE_EXIT_CODE)};
  }

  private class MyInstancesView extends JBPanel implements Disposable {
    private static final String HISTORY_ID_PREFIX = "filtering";
    private static final int MAX_TREE_NODE_COUNT = 1000;
    private static final int FILTERING_CHUNK_SIZE = 30;

    private final XDebuggerTree myInstancesTree;
    private final XDebuggerExpressionEditor myFilterConditionEditor;
    private final XDebugSessionListener myDebugSessionListener = new MySessionListener();

    private final MyCachedEvaluator myEvaluator = new MyCachedEvaluator();
    private final MyNodeManager myNodeManager = new MyNodeManager(myProject);

    private final JBProgressBar myFilteringProgressBar = new JBProgressBar();
    private final JButton myFilterButton = new JButton("Filter");
    private final JComponent myProgressPanel = new JBPanel<>(new BorderLayout());

    private final AnActionListener.Adapter myActionListener = new MyActionListener();
    private final Object myFilteringTaskLock = new Object();

    private volatile MyFilteringWorker myFilteringTask = null;

    MyInstancesView() {
      super(new BorderLayout(0, JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP)));

      XValueMarkers<?, ?> markers = getValueMarkers();
      ActionManager.getInstance().addAnActionListener(myActionListener, InstancesWindow.this.myDisposable);
      myDebugSession.addSessionListener(myDebugSessionListener, InstancesWindow.this.myDisposable);
      JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();

      myFilterConditionEditor = new ExpressionEditorWithHistory(myProject, editorsProvider,
          HISTORY_ID_PREFIX + myReferenceType.name(), null, InstancesWindow.this.myDisposable);
      setSourcePositionForEditor();

      myFilterButton.setBorder(BorderFactory.createEmptyBorder());
      Dimension filteringButtonSize = myFilterConditionEditor.getEditorComponent().getPreferredSize();
      filteringButtonSize.width = JBUI.scale(FILTERING_BUTTON_ADDITIONAL_WIDTH) +
          myFilterButton.getPreferredSize().width;
      myFilterButton.setPreferredSize(filteringButtonSize);

      JBPanel filteringPane = new JBPanel(new BorderLayout(JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP), 0));

      filteringPane.add(new JBLabel("Condition:"), BorderLayout.WEST);
      filteringPane.add(myFilterConditionEditor.getComponent(), BorderLayout.CENTER);
      filteringPane.add(myFilterButton, BorderLayout.EAST);

      JLabel stopFilteringButton =
          new JLabel(UIUtil.isUnderDarcula() ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight);
      stopFilteringButton.setOpaque(true);
      stopFilteringButton.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          cancelFilteringTask();
        }
      });

      myProgressPanel.add(myFilteringProgressBar, BorderLayout.CENTER);
      myProgressPanel.add(stopFilteringButton, BorderLayout.EAST);

      XDebuggerTreeCreator treeCreator =
          new XDebuggerTreeCreator(myProject, editorsProvider, null, markers);

      myInstancesTree = (XDebuggerTree) treeCreator.createTree(getTreeRootDescriptor());
      myInstancesTree.setRootVisible(false);
      myInstancesTree.getRoot().setLeaf(false);
      myInstancesTree.setExpandableItemsEnabled(true);

      myFilterButton.addActionListener(e -> {
        String expression = myFilterConditionEditor.getExpression().getExpression();
        if (!expression.isEmpty()) {
          myFilterConditionEditor.saveTextInHistory();
        }
        myInstancesTree.rebuildAndRestore(XDebuggerTreeState.saveState(myInstancesTree));
      });

      JBScrollPane treeScrollPane = new JBScrollPane(myInstancesTree);
      add(filteringPane, BorderLayout.NORTH);
      add(treeScrollPane, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {
      ActionManager.getInstance().removeAnActionListener(myActionListener);
      myDebugSession.removeSessionListener(myDebugSessionListener);
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
          List<ObjectReference> instances = myReferenceType.instances(0);
          EvaluationContextImpl evaluationContext = debugProcess
              .getDebuggerContext().createEvaluationContext();

          if (evaluationContext != null) {
            synchronized (myFilteringTaskLock) {
              myFilteringTask = new MyFilteringWorker(instances, evaluationContext, createEvaluator());
              myFilteringTask.execute();
            }
          }
        }
      });
    }

    private void showProgressPane(int progressBarMaximum) {
      myFilteringProgressBar.setMinimum(0);
      myFilteringProgressBar.setMaximum(progressBarMaximum);
      myProgressPanel.setVisible(true);
      myProgressPanel.repaint();
    }

    private void hideProgressPane() {
      myProgressPanel.setVisible(false);
      myProgressPanel.repaint();
    }

    @Nullable
    private ExpressionEvaluator createEvaluator() {
      ExpressionEvaluator evaluator = null;

      XExpression expression = myFilterConditionEditor.getExpression();
      if (expression != null && !expression.getExpression().isEmpty()) {
        try {
          myEvaluator.setReferenceExpression(TextWithImportsImpl.
              fromXExpression(expression));
          evaluator = myEvaluator.getEvaluator();
        } catch (EvaluateException ignored) {
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

    private void addChildrenToTree(XValueChildrenList children, boolean last) {
      XDebuggerTreeNode root = myInstancesTree.getRoot();
      if (root != null) {
        ((XValueNodeImpl) root).addChildren(children, last);
      }
    }

    private void setSourcePositionForEditor() {
      new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() throws Exception {
          ApplicationManager.getApplication().runReadAction(() -> {
            final PsiClass psiClass = DebuggerUtils.findClass(myReferenceType.name(),
                myProject, GlobalSearchScope.allScope(myProject));
            XSourcePositionImpl position = XSourcePositionImpl.createByElement(psiClass);
            SwingUtilities.invokeLater(() -> myFilterConditionEditor.setSourcePosition(position));
          });
          return null;
        }
      }.execute();
    }

    @NotNull
    private Pair<XValue, String> getTreeRootDescriptor() {
      return Pair.pair(new XValue() {
        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
          updateInstances();
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
          node.setPresentation(null, "", "", true);
        }
      }, "root");
    }

    private class MySessionListener implements XDebugSessionListener {
      private final XValueChildrenList myRunningAppChildNode = new XValueChildrenList();

      private volatile XDebuggerTreeState myTreeState = null;

      {
        myRunningAppChildNode.add(new XNamedValue("") {
          @Override
          public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            node.setPresentation(XDebuggerUIConstants.INFORMATION_MESSAGE_ICON, new XValuePresentation() {
              @NotNull
              @Override
              public String getSeparator() {
                return "";
              }

              @Override
              public void renderValue(@NotNull XValueTextRenderer renderer) {
                renderer.renderValue("The application is running");
              }
            }, false);
          }
        });
      }

      @Override
      public void sessionResumed() {
        SwingUtilities.invokeLater(() -> {
          myTreeState = XDebuggerTreeState.saveState(myInstancesTree);
          cancelFilteringTask();

          XDebuggerTreeNode root = myInstancesTree.getRoot();
          if (root != null) {
            root.clearChildren();
            addChildrenToTree(myRunningAppChildNode, true);
          }
        });
      }

      @Override
      public void sessionPaused() {
        SwingUtilities.invokeLater(() -> myInstancesTree.rebuildAndRestore(myTreeState));
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

            myInstancesTree.rebuildAndRestore(XDebuggerTreeState.saveState(myInstancesTree));
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
        return myReferenceType.name();
      }

      ExpressionEvaluator getEvaluator() throws EvaluateException {
        return getEvaluator(myProject);
      }
    }

    private class MyFilteringWorker extends SwingWorker<Void, Void> {
      private final List<ObjectReference> myReferences;
      private final EvaluationContextImpl myEvaluationContext;
      private final ExpressionEvaluator myExpressionEvaluator;

      private volatile boolean myDebuggerTaskCompleted = false;

      MyFilteringWorker(@NotNull List<ObjectReference> refs,
                        @NotNull EvaluationContextImpl evaluationContext,
                        @Nullable ExpressionEvaluator evaluator) {
        myReferences = refs;
        myEvaluationContext = evaluationContext;
        myExpressionEvaluator = evaluator;
      }

      @Override
      protected void done() {
        addChildrenToTree(XValueChildrenList.EMPTY, true);
        hideProgressPane();
      }

      @Override
      protected Void doInBackground() throws Exception {
        DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject)
            .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());

        SwingUtilities.invokeLater(() -> showProgressPane(myReferences.size()));

        AtomicInteger totalChildren = new AtomicInteger(0);
        for (int i = 0, size = myReferences.size(); i < size; i += FILTERING_CHUNK_SIZE) {
          myDebuggerTaskCompleted = false;
          final int chunkBegin = i;
          debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debugProcess.getDebuggerContext()) {
            @Override
            public Priority getPriority() {
              return Priority.LOWEST;
            }

            @Override
            public void threadAction(@NotNull SuspendContextImpl suspendContext) {
              XValueChildrenList children = new XValueChildrenList();
              int endOfChunk = min(chunkBegin + FILTERING_CHUNK_SIZE, size);
              for (int j = chunkBegin; j < endOfChunk && totalChildren.get() < MAX_TREE_NODE_COUNT; j++) {
                ObjectReference ref = myReferences.get(j);
                if (myExpressionEvaluator != null && isSatisfy(myExpressionEvaluator, ref) != MyFilteringResult.MATCH) {
                  continue;
                }

                JavaValue val = new InstanceJavaValue(null, new InstanceValueDescriptor(myProject, ref),
                    myEvaluationContext, myNodeManager, true);
                children.add(val);
              }

              if (children.size() > 0) {
                totalChildren.addAndGet(children.size());
                SwingUtilities.invokeLater(() -> {
                  if(MyFilteringWorker.this == myFilteringTask) {
                    addChildrenToTree(children, false);
                  }
                });
              }

              SwingUtilities.invokeLater(() -> myFilteringProgressBar.setValue(endOfChunk));

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
          if (totalChildren.get() >= MAX_TREE_NODE_COUNT) {
            break;
          }
        }

        return null;
      }

      private MyFilteringResult isSatisfy(@NotNull ExpressionEvaluator evaluator, @NotNull Value value) {
        try {
          Value result = evaluator.evaluate(myEvaluationContext.createEvaluationContext(value));
          if (result instanceof BooleanValue && ((BooleanValue) result).value()) {
            return MyFilteringResult.MATCH;
          }
        } catch (EvaluateException e) {
          return MyFilteringResult.EVAL_ERROR;
        }

        return MyFilteringResult.NO_MATCH;
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
