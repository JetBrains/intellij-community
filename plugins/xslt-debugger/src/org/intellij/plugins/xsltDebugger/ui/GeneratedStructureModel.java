/*
 * Copyright 2002-2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.reference.SoftReference;
import com.intellij.util.SmartList;
import com.intellij.util.containers.StringInterner;
import org.intellij.plugins.xsltDebugger.XsltDebuggerSession;
import org.intellij.plugins.xsltDebugger.rt.engine.OutputEventQueue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 09.06.2007
 */
public class GeneratedStructureModel extends DefaultTreeModel {
  @NonNls
  private static final String PENDING = "...";

  private static WeakReference<StringInterner> ourSharedInterner;

  private final LinkedList<DefaultMutableTreeNode> myCurrentPath = new LinkedList<>();
  private final List<DefaultMutableTreeNode> myLastNodes = new LinkedList<>();

  private final StringInterner myInterner = getInterner();

  // we keep a shared string interner across all currently running xslt debugger instances. it should go away once
  // all instances (and their toolwindow contents) are gone. This should minimize the memory usage of the generated
  // structure tree.
  private static StringInterner getInterner() {
    StringInterner interner = SoftReference.dereference(ourSharedInterner);
    if (interner == null) {
      interner = new StringInterner();
      ourSharedInterner = new WeakReference<>(interner);
    }
    return interner;
  }

  private boolean myFilterWhitespace;
  private boolean myListenersDisabled;

  public GeneratedStructureModel() {
    super(new MyRootNode());
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)getRoot();
    myCurrentPath.add(root);
    root.add(new DefaultMutableTreeNode(PENDING));
  }

  public void update(final List<OutputEventQueue.NodeEvent> eventQueue) {
    if (!SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(() -> updateImpl(eventQueue));
      return;
    }
    updateImpl(eventQueue);
  }

  @Override
  public Object getChild(Object parent, int index) {
    if (!myFilterWhitespace) {
      return super.getChild(parent, index);
    }
    return getFilteredChildren((DefaultMutableTreeNode)parent, false).get(index);
  }

  @Override
  public int getChildCount(Object parent) {
    if (!myFilterWhitespace) {
      return super.getChildCount(parent);
    }
    return getFilteredChildren((DefaultMutableTreeNode)parent, false).size();
  }

  @Override
  public boolean isLeaf(Object node) {
    if (!myFilterWhitespace) {
      return super.isLeaf(node);
    }
    return super.isLeaf(node) || getFilteredChildren((DefaultMutableTreeNode)node, true).size() == 0;
  }

  private static List getFilteredChildren(DefaultMutableTreeNode node, boolean checkOnly) {
    if (node.getChildCount() == 0) {
      return Collections.emptyList();
    }
    final List<DefaultMutableTreeNode> nodes = checkOnly ?
                                               new SmartList<>() :
                                               new ArrayList<>(node.getChildCount());

    DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getFirstChild();
    while (child != null) {
      if (child instanceof StructureNode) {
        final OutputEventQueue.NodeEvent event = (OutputEventQueue.NodeEvent)child.getUserObject();
        if (event != null && event.getType() == OutputEventQueue.CHARACTERS) {
          if (event.getValue().trim().length() == 0) {
            child = child.getNextSibling();
            continue;
          }
        }
      }

      nodes.add(child);
      if (checkOnly) return nodes;

      child = child.getNextSibling();
    }
    return nodes;
  }

  private void updateImpl(List<OutputEventQueue.NodeEvent> nodeEvents) {
    if (nodeEvents.size() > 0) {
      for (DefaultMutableTreeNode node : myLastNodes) {
        if (node instanceof StructureNode) {
          ((StructureNode)node).refresh();
        }
      }
      myLastNodes.clear();
    }

    for (OutputEventQueue.NodeEvent event : nodeEvents) {
      event = intern(event);

      final DefaultMutableTreeNode node = myCurrentPath.getFirst();
      switch (event.getType()) {
        case OutputEventQueue.START_DOCUMENT:
          break;
        case OutputEventQueue.START_ELEMENT:
          final StructureNode child = new StructureNode(event);
          myLastNodes.add(child);

          final int index = getChildCount(node) - 1;
          node.insert(child, node.getChildCount() - 1);
          child.add(new DefaultMutableTreeNode(PENDING));

          myCurrentPath.addFirst(child);

          nodesWereInserted(node, new int[]{ index });
          break;

        case OutputEventQueue.END_ELEMENT:
          if (node instanceof MyRootNode) {
            // unknown xalan bug: start/end is sometimes unbalanced. should be fixed somewhere else...
            continue;
          }
        case OutputEventQueue.END_DOCUMENT:
          final DefaultMutableTreeNode c = myCurrentPath.removeFirst();
          final int childIndex = getChildCount(c) - 1;
          final int realChildIndex = c.getChildCount() - 1;
          if (realChildIndex >= 0) {
            final DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)c.getChildAt(realChildIndex);
            assert childNode.getUserObject() == PENDING;
            c.remove(realChildIndex);
            nodesWereRemoved(c, new int[]{ childIndex }, new Object[]{ childNode });
          }
          break;

        case OutputEventQueue.TRACE_POINT:
        case OutputEventQueue.ATTRIBUTE:
        case OutputEventQueue.CHARACTERS:
        case OutputEventQueue.COMMENT:
        case OutputEventQueue.PI:
          final StructureNode ch = new StructureNode(event);
          myLastNodes.add(ch);

          final int i = getChildCount(node) - 1;
          node.insert(ch, node.getChildCount() - 1);
          nodesWereInserted(node, new int[]{ i });
      }
    }
  }

  private OutputEventQueue.NodeEvent intern(OutputEventQueue.NodeEvent event) {
    event.myURI = intern(event.myURI);
    event.myQName = intern(event.myQName);
    event.myValue = intern(event.myValue);
    return event;
  }

  @Nullable
  private OutputEventQueue.NodeEvent.QName intern(OutputEventQueue.NodeEvent.QName name) {
    if (name == null) return null;
    name.myPrefix = intern(name.myPrefix);
    name.myLocalName = intern(name.myLocalName);
    name.myURI = intern(name.myURI);
    return name;
  }

  @Nullable
  private String intern(String s) {
    if (s != null) {
      if (s.length() == 0) return s.intern();
      return myInterner.intern(s);
    } else {
      return null;
    }
  }

  public boolean isFilterWhitespace() {
    return myFilterWhitespace;
  }

  public void setFilterWhitespace(boolean b) {
    final boolean old = myFilterWhitespace;
    myFilterWhitespace = b;
    if (b != old) {
      nodeStructureChanged((TreeNode)getRoot());
    }
  }

  public void finalUpdate(final List<OutputEventQueue.NodeEvent> events) {
    Runnable runnable = () -> {
      myListenersDisabled = true;
      try {
        updateImpl(events);
      } finally {
        myListenersDisabled = false;
        nodeStructureChanged((TreeNode)getRoot());
      }
    };
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  @Override
  protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    if (myListenersDisabled) return;
    super.fireTreeNodesChanged(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
    if (myListenersDisabled) return;
    super.fireTreeNodesInserted(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices, Object[] children) {
    if (myListenersDisabled) return;
    super.fireTreeNodesRemoved(source, path, childIndices, children);
  }

  @Override
  protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
    if (myListenersDisabled) return;
    super.fireTreeStructureChanged(source, path, childIndices, children);
  }

  private static class MyRootNode extends DefaultMutableTreeNode {
    public MyRootNode() {
      super("ROOT");
    }
  }

  public static class StructureNode extends DefaultMutableTreeNode implements Navigatable {
    private boolean isNew = true;

    public StructureNode(OutputEventQueue.NodeEvent event) {
      super(event);
    }

    private void refresh() {
      isNew = false;
    }

    public boolean isNew() {
      return isNew;
    }

    @Override
    public OutputEventQueue.NodeEvent getUserObject() {
      return (OutputEventQueue.NodeEvent)super.getUserObject();
    }

    public void navigate(boolean requestFocus) {
      final OutputEventQueue.NodeEvent event = getUserObject();
      final Project project = (Project)DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT.getName());
      XsltDebuggerSession.openLocation(project, event.getURI(), event.getLineNumber() - 1);
    }

    public boolean canNavigate() {
      return getUserObject().getLineNumber() > 0;
    }

    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }
}
