/*
 * Class DebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.impl.DebuggerTreeBase;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.render.ChildrenBuilder;
import com.intellij.debugger.ui.tree.render.NodeRendererSettings;
import com.intellij.debugger.ui.tree.render.NodeRendererSettingsListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.IJSwingUtilities;
import com.sun.jdi.*;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public abstract class DebuggerTree extends DebuggerTreeBase implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DebuggerTree");
  private final Key VISIBLE_RECT = new Key("VISIBLE_RECT");

  private final Project myProject;
  private final NodeManagerImpl myDescriptorManager;

  private int     myPriority = DebuggerManagerThreadImpl.NORMAL_PRIORITY;
  private NodeRendererSettingsListener mySettingsListener;
  private DebuggerContextImpl myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;

  public DebuggerTree(Project project) {
    super(null, project);
    setScrollsOnExpand(false);
    myDescriptorManager = new NodeManagerImpl(project, this);
    TreeBuilder model = new TreeBuilder(this) {
      protected void buildChildren(TreeBuilderNode node) {
        if (((DebuggerTreeNodeImpl)node).getDescriptor() instanceof DefaultNodeDescriptor) return;
        buildNode((DebuggerTreeNodeImpl)node);
      }

      protected boolean isExpandable(TreeBuilderNode builderNode) {
        return DebuggerTree.this.isExpandable((DebuggerTreeNodeImpl)builderNode);
      }
    };
    model.setRoot(getNodeFactory().getDefaultNode());
    model.addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged     (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeNodesInserted    (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeNodesRemoved     (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeStructureChanged (TreeModelEvent event) { myTipManager.hideTooltip(); }
    });

    setModel(model);

    myProject = project;
    new TreeSpeedSearch(this);
  }

  private void installSettingsListener() {
    if (mySettingsListener != null) return;
    mySettingsListener = new NodeRendererSettingsListener() {
      private void rendererSettingsChanged(DebuggerTreeNodeImpl node) {
        if (node.getDescriptor() instanceof ValueDescriptorImpl) {
          node.calcRepresentation();
        }

        try {
          for (Enumeration e = node.rawChildren(); e.hasMoreElements();) {
            DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
            rendererSettingsChanged(child);
          }
        }
        catch (NoSuchElementException e) {
        }
      }

      public void renderersChanged() {
        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
        if (root != null) {
          rendererSettingsChanged(root);
        }

      }
    };
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
  }

  private void uninstallSettingsListener() {
    if (mySettingsListener == null) return;
    NodeRendererSettings.getInstance().removeListener(mySettingsListener);
    mySettingsListener = null;
  }

  public void removeNotify() {
    uninstallSettingsListener();
    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();
    installSettingsListener();
  }

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    return descriptor.isExpandable();
  }

  public Object getData(String dataId) {
    if (DebuggerActions.DEBUGGER_TREE.equals(dataId)) return this;
    return null;
  }


  private void buildNode(final DebuggerTreeNodeImpl node) {
    if (node == null || node.getDescriptor() == null) return;

    BuildNodeCommand builder = getBuildNodeCommand(node);
    builder.getNode().add(myDescriptorManager.createMessageNode(MessageDescriptor.EVALUATING));
    getDebuggerContext().getDebugProcess().getManagerThread().invokeLater(builder);
  }

  private BuildNodeCommand getBuildNodeCommand(final DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() instanceof ThreadGroupDescriptorImpl) {
      return new BuildThreadGroupCommand(node);
    }
    else if (node.getDescriptor() instanceof ThreadDescriptorImpl) {
      return new BuildThreadCommand(node);
    }
    else if (node.getDescriptor() instanceof StackFrameDescriptorImpl) {
      return new BuildStackFrameCommand(node);
    }
    else if (node.getDescriptor() instanceof ValueDescriptorImpl) {
      return new BuildValueNodeCommand(node);
    }
    else if (node.getDescriptor() instanceof StaticDescriptorImpl) {
      return new BuildStaticNodeCommand(node);
    }
    LOG.assertTrue(false);
    return null;
  }

  public void saveState(DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() != null) {
      TreePath path = new TreePath(node.getPath());
      node.getDescriptor().myIsExpanded = isExpanded(path);
      node.getDescriptor().myIsSelected = getSelectionModel().isPathSelected(path);
      Rectangle rowBounds = getRowBounds(getRowForPath(path));
      if(rowBounds != null && getVisibleRect().contains(rowBounds)) {
        node.getDescriptor().putUserData(VISIBLE_RECT, getVisibleRect());
      }
      else {
        node.getDescriptor().putUserData(VISIBLE_RECT, null);
      }
    }

    for (Enumeration e = node.rawChildren(); e.hasMoreElements();) {
      DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
      saveState(child);
    }
  }

  public void restoreState(DebuggerTreeNodeImpl node) {
    if(!node.myIsStateRestored) {
      restoreNodeState(node);
    }
    else {
      node.myIsStateRestored = true;      
    }
    if (node.getDescriptor().myIsExpanded) {
      for (Enumeration e = node.rawChildren(); e.hasMoreElements();) {
        DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
        restoreState(child);
      }
    }
  }

  public void restoreState() {
    clearSelection();
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    if (root != null) {
      restoreState(root);
    }
  }

  protected void restoreNodeState(DebuggerTreeNodeImpl node) {
    Rectangle visibleRect = (Rectangle) node.getDescriptor().getUserData(VISIBLE_RECT);
    if(visibleRect != null) {
      scrollRectToVisible(visibleRect);
    }
    if (node.getDescriptor() != null) {
      if (node.getParent() == null) node.getDescriptor().myIsExpanded = true;

      TreePath path = new TreePath(node.getPath());
      if (node.getDescriptor().myIsExpanded) {
        expandPath(path);
      }
      if (node.getDescriptor().myIsSelected) {
        addSelectionPath(path);
      }
//      if(node.getDescriptor().myIsVisible) {
//        scrollPathToVisible(path);
//      }
    }
  }

  public NodeManagerImpl getNodeFactory() {
    return myDescriptorManager;
  }

  public TreeBuilder getMutableModel() {
    return (TreeBuilder)getModel();
  }

  public void removeAllChildren() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    root.removeAllChildren();
    treeChanged();
  }

  public void showMessage(MessageDescriptor messageDesc) {
    DebuggerTreeNodeImpl root = getNodeFactory().getDefaultNode();
    getMutableModel().setRoot(root);
    DebuggerTreeNodeImpl message = root.add(messageDesc);
    treeChanged();
    expandPath(new TreePath(message.getPath()));
  }

  public void showMessage(String messageText) {
    showMessage(new MessageDescriptor(messageText));
  }

  public void treeChanged() {
    DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)getModel().getRoot();
    if (node != null) {
      getMutableModel().nodeStructureChanged(node);
      restoreState();
    }
  }

  public Project getProject() { return myProject; }

  public int getPriority() {
    return myPriority;
  }

  public void setEvaluationPriority(int priority) {
    myPriority = priority;
  }

  protected abstract void build(DebuggerContextImpl context);

  public final void buildWhenPaused(DebuggerContextImpl context, RefreshDebuggerTreeCommand command) {
    DebuggerSession debuggerSession = context.getDebuggerSession();

    if(ApplicationManager.getApplication().isUnitTestMode() || debuggerSession.getState() == DebuggerSession.STATE_PAUSED) {
      showMessage(MessageDescriptor.EVALUATING);
      context.getDebugProcess().getManagerThread().invokeLater(command);
    }
    else {
      showMessage(context.getDebuggerSession().getStateDescription());
    }
  }

  public void rebuild(final DebuggerContextImpl context) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    myDebuggerContext = context;
    saveState();
    context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context){
      public void threadAction() {
        getNodeFactory().setHistoryByContext(context);
      }
    });

    build(context);
  }

  public void saveState() {
    saveState((DebuggerTreeNodeImpl)getModel().getRoot());
  }

  protected abstract class RefreshDebuggerTreeCommand extends DebuggerContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    public RefreshDebuggerTreeCommand(DebuggerContextImpl context) {
      super(context);
      myDebuggerContext = context;
    }

    public DebuggerContextImpl getDebuggerContext() {
      return myDebuggerContext;
    }

    public void threadAction() {

    }
  }

  public static abstract class InplaceEditor {
    final DebuggerSession myDebuggerSession;
    final DebuggerTreeNodeImpl myNode;
    JComponent myEditorComponent;

    private ContentManagerAdapter mySelectionListener = new ContentManagerAdapter() {
      public void selectionChanged(ContentManagerEvent event) {
        doFocusLostAction();
      }
    };;

    private KeyAdapter myKeyListener = new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if(!isShowed()) return;
        if(e.getKeyCode() == KeyEvent.VK_ENTER) {
          doOKAction();
        } else if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          doCancelAction();
        }
      }
    };
    private FocusAdapter myFocusListener = new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        if(!isShowed()) return;
        if(e.isTemporary()) return;
        if(myEditorComponent.getParent() == null) return;
        doFocusLostAction();
      }
    };
    private ComponentAdapter myComponentListener = new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
              DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
                          public void run() {
                            if(!isShowed()) return;
                            JTree tree = myNode.getTree();
                            JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();
                            Rectangle bounds = getEditorBounds();
                            Point layeredPanePoint=SwingUtilities.convertPoint(tree, bounds.x, bounds.y,layeredPane);
                            myEditorComponent.setBounds(layeredPanePoint.x,layeredPanePoint.y, bounds.width, bounds.height);
                            myEditorComponent.revalidate();
                          }
                        });
            }

            public void componentHidden(ComponentEvent e) {
              doFocusLostAction();
            }
          };
    private RunContentListener myRunContentListener = new RunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor) {
        doFocusLostAction();
      }

      public void contentRemoved(RunContentDescriptor descriptor) {
        doFocusLostAction();
      }
    };

    protected abstract JComponent       createEditorComponent();
    protected abstract JComponent       getContentComponent  ();
    public    abstract Editor           getEditor            ();

    public void doOKAction() {
      remove();
    }

    public void doFocusLostAction() {
      doCancelAction();
    }

    public void doCancelAction() {
      remove();
    }

    private void remove() {
      if(!isShowed()) return;
      DebuggerTree tree = myNode.getTree();
      JRootPane rootPane = tree.getRootPane();
      if (rootPane != null) {
        JLayeredPane layeredPane = rootPane.getLayeredPane();
        if(layeredPane != null) {
          layeredPane.remove(myEditorComponent);          
        }
        rootPane.removeComponentListener(myComponentListener);
      }
      if(myDebuggerSession != null) {
//        ContentManager manager = myDebuggerSession.getViewsContentManager();
//        manager.removeContentManagerListener(mySelectionListener);
      }

      ExecutionManager.getInstance(getProject()).getContentManager().removeRunContentListener(myRunContentListener);

      myEditorComponent = null;

      tree.repaint();
      tree.requestFocus();
    }

    private Project getProject() {
      return myNode.getTree().getProject();
    }

    public InplaceEditor(DebuggerTreeNodeImpl node) {
      myNode = node;
      myDebuggerSession = node.getTree().getDebuggerContext().getDebuggerSession();
    }

    public DebuggerTreeNodeImpl getNode() {
      return myNode;
    }

    public void show() {
      LOG.assertTrue(myEditorComponent == null, "editor is not released");
      final DebuggerTree tree = myNode.getTree();
      final JLayeredPane layeredPane = tree.getRootPane().getLayeredPane();

      Rectangle bounds = getEditorBounds();

      Point layeredPanePoint=SwingUtilities.convertPoint(tree, bounds.x, bounds.y,layeredPane);

      myEditorComponent = createEditorComponent();
      LOG.assertTrue(myEditorComponent != null);
      myEditorComponent.setBounds(
        layeredPanePoint.x,
        layeredPanePoint.y,
        bounds.width,
        Math.max(bounds.height, myEditorComponent.getPreferredSize().height));

      layeredPane.add(myEditorComponent,new Integer(250));

      myEditorComponent.validate();
      myEditorComponent.paintImmediately(0,0,myEditorComponent.getWidth(),myEditorComponent.getHeight());
      getContentComponent().requestFocus();

      tree.getRootPane().addComponentListener(myComponentListener);
      if(myDebuggerSession != null) {
//        myDebuggerSession.getViewsContentManager().addContentManagerListener(mySelectionListener);
      }
      ExecutionManager.getInstance(getProject()).getContentManager().addRunContentListener(myRunContentListener);
      ListenerUtil.addKeyListener(getContentComponent(), myKeyListener);
      ListenerUtil.addFocusListener(getContentComponent(), myFocusListener);
    }

    private Rectangle getEditorBounds() {
      final DebuggerTree tree = myNode.getTree();
      Rectangle bounds = tree.getVisibleRect();
      Rectangle nodeBounds = tree.getPathBounds(new TreePath(myNode.getPath()));
      bounds.y = nodeBounds.y;
      bounds.height = nodeBounds.height;

      if(nodeBounds.x > bounds.x) {
        bounds.width = bounds.width - nodeBounds.x + bounds.x;
        bounds.x = nodeBounds.x;
      }
      return bounds;
    }

    public boolean isShowed() {
      return myEditorComponent != null;
    }
  }

  public DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public abstract class BuildNodeCommand extends DebuggerContextCommandImpl {
    private final DebuggerTreeNodeImpl myNode;

    protected final List<DebuggerTreeNodeImpl> myChildren = new LinkedList<DebuggerTreeNodeImpl>();

    protected BuildNodeCommand(DebuggerTreeNodeImpl node) {
      super(DebuggerTree.this.getDebuggerContext());
      myNode = node;
    }

    public DebuggerTreeNodeImpl getNode() {
      return myNode;
    }

    protected void updateUI() {
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            myNode.removeAllChildren();
            for (Iterator<DebuggerTreeNodeImpl> iterator = myChildren.iterator(); iterator.hasNext();) {
              DebuggerTreeNodeImpl debuggerTreeNode = iterator.next();
              myNode.add(debuggerTreeNode);
            }
            myNode.childrenChanged();
          }
        });
    }
  }

  private class BuildStackFrameCommand extends BuildNodeCommand {
    public BuildStackFrameCommand(DebuggerTreeNodeImpl stackNode) {
      super(stackNode);
    }

    public void threadAction() {
      try {
        StackFrameDescriptorImpl stackDescriptor = (StackFrameDescriptorImpl)getNode().getDescriptor();
        StackFrameProxyImpl frame = stackDescriptor.getStackFrame();
        if(!getDebuggerContext().getDebugProcess().getSuspendManager().isSuspended(frame.threadProxy())) return;

        LOG.assertTrue(frame.threadProxy().isSuspended());

        ObjectReference thisObjectReference = frame.thisObject();

        EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

        if (thisObjectReference != null) {
          myChildren.add(myDescriptorManager.createNode(myDescriptorManager.getThisDescriptor(stackDescriptor, thisObjectReference), evaluationContext));
        }
        else {
          ReferenceType type = frame.location().method().declaringType();
          myChildren.add(myDescriptorManager.createNode(myDescriptorManager.getStaticDescriptor(stackDescriptor, type), evaluationContext));
        }
        try {
          for (Iterator<LocalVariableProxyImpl> iterator = frame.visibleVariables().iterator(); iterator.hasNext();) {
            LocalVariableProxyImpl local = iterator.next();

            myChildren.add(myDescriptorManager.createNode(myDescriptorManager.getLocalVariableDescriptor(stackDescriptor, local), evaluationContext));
          }
        }
        catch (EvaluateException e) {
          myChildren.add(myDescriptorManager.createMessageNode(new MessageDescriptor(e.getMessage())));
        }
      }
      catch (EvaluateException e) {
        myChildren.clear();
        myChildren.add(myDescriptorManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }

      updateUI();
    }
  }

  private class BuildThreadCommand extends BuildNodeCommand {
    public BuildThreadCommand(DebuggerTreeNodeImpl threadNode) {
      super(threadNode);
    }

    public void threadAction() {
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)getNode().getDescriptor());
      ThreadReferenceProxyImpl threadProxy = threadDescriptor.getThreadReference();
      if (!threadProxy.isCollected() && getDebuggerContext().getDebugProcess().getSuspendManager().isSuspended(threadProxy)) {
        int status = threadProxy.status();
        if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) &&
            !(status == ThreadReference.THREAD_STATUS_NOT_STARTED) &&
            !(status == ThreadReference.THREAD_STATUS_ZOMBIE)) {
          try {
            for (Iterator it = threadProxy.frames().iterator(); it.hasNext();) {
              StackFrameProxyImpl stackFrame = (StackFrameProxyImpl)it.next();
              //Method method = stackFrame.location().method();
              //ToDo :check whether is synthetic if (shouldDisplay(method)) {
              myChildren.add(myDescriptorManager.createNode(myDescriptorManager.getStackFrameDescriptor(threadDescriptor, stackFrame), getDebuggerContext().createEvaluationContext()));
            }
          }
          catch (EvaluateException e) {
            myChildren.clear();
            myChildren.add(myDescriptorManager.createMessageNode(e.getMessage()));
            LOG.debug(e);
            //LOG.assertTrue(false);
            // if we pause during evaluation of this method the exception is thrown
            //  private static void longMethod(){
            //    try {
            //      Thread.sleep(100000);
            //    } catch (InterruptedException e) {
            //      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            //    }
            //  }
          }
        }
      }
      updateUI();
    }
  }

  private class BuildThreadGroupCommand extends BuildNodeCommand {
    public BuildThreadGroupCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      ThreadGroupDescriptorImpl groupDescriptor = (ThreadGroupDescriptorImpl)getNode().getDescriptor();
      ThreadGroupReferenceProxyImpl threadGroup = groupDescriptor.getThreadGroupReference();

      List threads = new ArrayList(threadGroup.threads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

      boolean showCurrent = ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD;

      for (Iterator it = threadGroup.threadGroups().iterator(); it.hasNext();) {
        ThreadGroupReferenceProxyImpl group = (ThreadGroupReferenceProxyImpl)it.next();
        if (group != null) {
          DebuggerTreeNodeImpl threadNode = myDescriptorManager.createNode(myDescriptorManager.getThreadGroupDescriptor(groupDescriptor, group), evaluationContext);

          if (showCurrent && ((ThreadGroupDescriptorImpl)threadNode.getDescriptor()).isCurrent()) {
            myChildren.add(0, threadNode);
          }
          else {
            myChildren.add(threadNode);
          }
        }
      }

      ArrayList<DebuggerTreeNodeImpl> threadNodes = new ArrayList<DebuggerTreeNodeImpl>();

      for (Iterator it = threads.iterator(); it.hasNext();) {
        ThreadReferenceProxyImpl thread = (ThreadReferenceProxyImpl)it.next();
        if (thread != null) {
          DebuggerTreeNodeImpl threadNode = myDescriptorManager.createNode(myDescriptorManager.getThreadDescriptor(groupDescriptor, thread), evaluationContext);
          if (showCurrent && ((ThreadDescriptorImpl)threadNode.getDescriptor()).isCurrent()) {
            threadNodes.add(0, threadNode);
          }
          else {
            threadNodes.add(threadNode);
          }
        }
      }

      myChildren.addAll(threadNodes);

      updateUI();
    }
  }

  private class BuildValueNodeCommand extends BuildNodeCommand {
    public BuildValueNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      ValueDescriptorImpl descriptor = (ValueDescriptorImpl)getNode().getDescriptor();
      try {
        descriptor.getRenderer(getSuspendContext().getDebugProcess()).buildChildren(descriptor.getValue(), new ChildrenBuilder() {
          public NodeManagerImpl getNodeManager() {
            return myDescriptorManager;
          }

          public NodeManagerImpl getDescriptorManager() {
            return myDescriptorManager;
          }

          public ValueDescriptorImpl getParentDescriptor() {
            return (ValueDescriptorImpl)getNode().getDescriptor();
          }

          public void setChildren(final List<DebuggerTreeNode> children) {
            IJSwingUtilities.invoke(new Runnable() {
              public void run() {
                getNode().removeAllChildren();
                for (Iterator<DebuggerTreeNode> iterator = children.iterator(); iterator.hasNext();) {
                  DebuggerTreeNode debuggerTreeNode = iterator.next();
                  getNode().add(debuggerTreeNode);
                }
                getNode().childrenChanged();
              }
            });
          }
        }, getDebuggerContext().createEvaluationContext());
      }
      catch (ObjectCollectedException e) {
        getNode().removeAllChildren();
        getNode().add(getNodeFactory().createMessageNode(new MessageDescriptor("Cannot evaluate descendants, object was collected. " + e.getMessage())));
        getNode().childrenChanged();
      }
    }
  }

  private class BuildStaticNodeCommand extends BuildNodeCommand {
    public BuildStaticNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      final StaticDescriptorImpl sd = (StaticDescriptorImpl)getNode().getDescriptor();
      final ReferenceType refType = sd.getType();
      List fields = refType.allFields();
      for (Iterator it = fields.iterator(); it.hasNext();) {
        Field field = (Field)it.next();
        if (field.isStatic()) {
          final FieldDescriptorImpl fieldDescriptor = myDescriptorManager.getFieldDescriptor(sd, null, field);
          final EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
          final DebuggerTreeNodeImpl node = myDescriptorManager.createNode(fieldDescriptor, evaluationContext);
          myChildren.add(node);
        }
      }

      updateUI();
    }
  }

}