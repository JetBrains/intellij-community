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
package com.intellij.debugger.streams.ui;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.memory.utils.InstanceJavaValue;
import com.intellij.debugger.memory.utils.InstanceValueDescriptor;
import com.intellij.debugger.streams.trace.smart.TraceElement;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class CollectionTree extends XDebuggerTree implements TraceContainer {
  private static final TreePath[] EMPTY_PATHS = new TreePath[0];

  private final NodeManagerImpl myNodeManager;
  private final Project myProject;
  private final Map<TraceElement, TreePath> myValue2Path = new HashMap<>();
  private final Map<TreePath, TraceElement> myPath2Value = new HashMap<>();
  private Set<TreePath> myHighlighted = Collections.emptySet();
  private final EventDispatcher<ValuesSelectionListener> myDispatcher = EventDispatcher.create(ValuesSelectionListener.class);

  private boolean myIgnoreSelectionEvents = false;
  private boolean myIgnoreClearSelection = false;

  CollectionTree(@NotNull List<TraceElement> values, @NotNull EvaluationContextImpl evaluationContext) {
    super(evaluationContext.getProject(), new JavaDebuggerEditorsProvider(), null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, null);

    myProject = evaluationContext.getProject();
    myNodeManager = new MyNodeManager(myProject);
    final XValueNodeImpl root = new XValueNodeImpl(this, null, "root", new MyRootValue(values, evaluationContext));
    setRoot(root, false);
    root.setLeaf(false);

    setCellRenderer(new TraceTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        super.customizeCellRenderer(tree, value, true, expanded, leaf, row, true);
        if (value instanceof XValueNodeImpl) {
          final XValueNodeImpl node = (XValueNodeImpl)value;
          final TreePath path = node.getPath();
          if (myHighlighted.contains(path)) {
            setIcon(AllIcons.Debugger.ThreadStates.Idle);
          }
        }
      }
    });

    getTreeModel().addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeNodesInserted(TreeModelEvent event) {
        final Object[] children = event.getChildren();
        for (int i = 0; i < children.length; i++) {
          Object child = children[i];
          if (child instanceof XValueNodeImpl) {
            final XValueNodeImpl node = (XValueNodeImpl)child;
            myValue2Path.put(values.get(i), node.getPath());
            myPath2Value.put(node.getPath(), values.get(i));
          }
        }

        getTreeModel().removeTreeModelListener(this);
      }
    });

    addTreeSelectionListener(e -> {
      if (myIgnoreSelectionEvents) {
        return;
      }

      @Nullable final TreePath[] selectedPaths = getSelectionPaths();

      @NotNull final TreePath[] paths = selectedPaths == null ? EMPTY_PATHS : selectedPaths;
      final List<TraceElement> selectedItems =
        Arrays.stream(paths)
          .map(CollectionTree::getTopPath)
          .map(myPath2Value::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      myIgnoreClearSelection = true;
      myDispatcher.getMulticaster().selectionChanged(selectedItems);
      myIgnoreClearSelection = false;
    });

    setSelectionRow(0);
    expandNodesOnLoad(node -> node == root);
  }

  @Override
  public void highlight(@NotNull List<TraceElement> newSelection) {
    myIgnoreSelectionEvents = true;
    if (!myIgnoreClearSelection) {
      clearSelection();
    }

    myHighlighted = newSelection.stream().map(myValue2Path::get).collect(Collectors.toSet());

    revalidate();
    repaint();
    myIgnoreSelectionEvents = false;
  }

  @Override
  public void addSelectionListener(@NotNull ValuesSelectionListener listener) {
    // TODO: dispose?
    myDispatcher.addListener(listener);
  }

  private class MyRootValue extends XValue {
    private final List<TraceElement> myValues;
    private final EvaluationContextImpl myEvaluationContext;

    MyRootValue(@NotNull List<TraceElement> values, @NotNull EvaluationContextImpl evaluationContext) {
      myValues = values;
      myEvaluationContext = evaluationContext;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      final XValueChildrenList children = new XValueChildrenList();
      for (TraceElement traceElement : myValues) {
        children
          .add(new InstanceJavaValue(new InstanceValueDescriptor(myProject, traceElement.getValue()), myEvaluationContext, myNodeManager));
      }

      node.addChildren(children, true);
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(null, "", "", true);
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

  private static TreePath getTopPath(@NotNull TreePath path) {
    while (path.getPathCount() > 2) {
      path = path.getParentPath();
    }

    return path;
  }
}
