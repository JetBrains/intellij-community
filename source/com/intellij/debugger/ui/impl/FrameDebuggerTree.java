/*
 * Class FrameDebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.debugger.DebuggerInvocationUtil;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

public class FrameDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.FrameDebuggerTree");
  private boolean myAnyNewLocals;

  public FrameDebuggerTree(Project project) {
    super(project);
  }

  protected void build(DebuggerContextImpl context) {
    myAnyNewLocals = false;
    buildWhenPaused(context, new RefreshFrameTreeCommand(context));
  }

  public void restoreNodeState(DebuggerTreeNodeImpl node) {
    if(myAnyNewLocals) {
      node.getDescriptor().myIsSelected = node.getDescriptor().myIsSelected && node.getDescriptor() instanceof LocalVariableDescriptorImpl;
    }
    super.restoreNodeState(node);
    if(myAnyNewLocals && node.getDescriptor().myIsExpanded) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getMutableModel().getRoot();
      DebuggerTreeNodeImpl lastSelected = null;
      for(Enumeration<DebuggerTreeNodeImpl> children = root.children(); children.hasMoreElements();) {
        DebuggerTreeNodeImpl child = children.nextElement();
        if(child.getDescriptor().myIsSelected) {
          lastSelected = child;
        }
      }

      if(lastSelected != null) {
        scrollPathToVisible(new TreePath(lastSelected.getPath()));
      }
    }
  }

  private class RefreshFrameTreeCommand extends RefreshDebuggerTreeCommand {
    public RefreshFrameTreeCommand(DebuggerContextImpl context) {
      super(context);
    }

    public void threadAction() {
      super.threadAction();
      DebuggerTreeNodeImpl rootNode;

      final ThreadReferenceProxyImpl currentThread = getDebuggerContext().getThreadProxy();
      if(currentThread == null) return;

      try {
        StackFrameProxyImpl frame = getDebuggerContext().getFrameProxy();

        if (frame != null) {
          NodeManagerImpl nodeManager = getNodeFactory();
          rootNode = nodeManager.createNode(nodeManager.getStackFrameDescriptor(null, frame), getDebuggerContext().createEvaluationContext());
        } else {
          rootNode = getNodeFactory().getDefaultNode();
          SuspendManager suspendManager = getSuspendContext().getDebugProcess().getSuspendManager();
          if(suspendManager.isSuspended(currentThread)) {
            try {
              if(currentThread.frameCount() == 0) {
                rootNode.add(MessageDescriptor.THREAD_IS_EMPTY);
              } else {
                rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
              }
            }
            catch (EvaluateException e) {
              rootNode.add(new MessageDescriptor(e.getMessage()));
            }
          } else {
            rootNode.add(MessageDescriptor.THREAD_IS_RUNNING);
          }
        }
      }
      catch (Exception ex) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(ex);
        }
        rootNode = getNodeFactory().getDefaultNode();
        rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
      }

      final DebuggerTreeNodeImpl rootNode1 = rootNode;
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          getMutableModel().setRoot(rootNode1);
          treeChanged();

          getModel().addTreeModelListener(new TreeModelAdapter() {
            public void treeStructureChanged(TreeModelEvent e) {
              Object[] path = e.getPath();
              if(path.length > 0 && path[path.length - 1] == rootNode1) {
                if(ViewsGeneralSettings.getInstance().AUTOSCROLL_TO_NEW_LOCALS) {
                  autoscrollToNewLocals(rootNode1);
                }
              }
            }
          });
        }
        private void autoscrollToNewLocals(DebuggerTreeNodeImpl frameNode) {
          for (Enumeration e  = frameNode.rawChildren(); e.hasMoreElements();) {
            DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
            NodeDescriptorImpl descriptor = child.getDescriptor();
            if(descriptor != null) {
              if (descriptor instanceof LocalVariableDescriptorImpl) {
                if (((LocalVariableDescriptorImpl) descriptor).isNewLocal() && getDebuggerContext().getDebuggerSession().isSteppingThrough(getDebuggerContext().getThreadProxy())) {
                  TreePath treePath = new TreePath(child.getPath());
                  addSelectionPath   (treePath);
                  scrollPathToVisible(treePath);
                  myAnyNewLocals = true;
                  descriptor.myIsSelected = true;
                }
                else {
                  removeSelectionPath(new TreePath(child.getPath()));
                  descriptor.myIsSelected = false;
                }
                ((LocalVariableDescriptorImpl) descriptor).setNewLocal(false);
              }
            }
          }
        }
      });
    }
  }

}