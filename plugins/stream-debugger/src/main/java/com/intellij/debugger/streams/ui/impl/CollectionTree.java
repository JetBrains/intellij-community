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

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.memory.utils.InstanceJavaValue;
import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.ui.PaintingListener;
import com.intellij.debugger.streams.ui.TraceContainer;
import com.intellij.debugger.streams.ui.ValuesSelectionListener;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeListener;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.Value;
import icons.StreamDebuggerIcons;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;
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
  private final EventDispatcher<ValuesSelectionListener> mySelectionDispatcher = EventDispatcher.create(ValuesSelectionListener.class);
  private final EventDispatcher<PaintingListener> myPaintingDispatcher = EventDispatcher.create(PaintingListener.class);

  private boolean myIgnoreInternalSelectionEvents = false;
  private boolean myIgnoreExternalSelectionEvents = false;

  CollectionTree(@NotNull List<Value> values,
                 @NotNull List<TraceElement> traceElements,
                 @NotNull EvaluationContextImpl evaluationContext) {
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
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof XValueNodeImpl) {
          final XValueNodeImpl node = (XValueNodeImpl)value;
          final TreePath path = node.getPath();
          if (myHighlighted.contains(path)) {
            setIcon(StreamDebuggerIcons.VALUE_HIGHLIGHTED_ICON);
          }
        }
      }
    });

    final Map<Value, List<TraceElement>> map2TraceElement = StreamEx.of(traceElements).groupingBy(TraceElement::getValue);

    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        final XDebuggerTreeListener listener = this;
        if (node instanceof XValueContainerNode) {
          final XValueContainer container = ((XValueContainerNode)node).getValueContainer();
          if (container instanceof JavaValue) {
            final ValueDescriptorImpl descriptor = ((JavaValue)container).getDescriptor();
            evaluationContext.getDebugProcess().getManagerThread().schedule(new DebuggerCommandImpl() {
              @Override
              protected void action() throws Exception {
                final Value value = descriptor.getValue();
                ApplicationManager.getApplication().invokeLater(() -> {
                  final List<TraceElement> trace = map2TraceElement.get(value);
                  if (trace != null && !trace.isEmpty()) {
                    final TraceElement head = trace.get(0);
                    myValue2Path.put(head, node.getPath());
                    myPath2Value.put(node.getPath(), head);
                    map2TraceElement.put(value, tail(trace));
                  }

                  if (myPath2Value.size() == traceElements.size()) {
                    CollectionTree.this.removeTreeListener(listener);
                  }
                });
              }
            });
          }
        }
      }
    });

    addTreeSelectionListener(e -> {
      if (myIgnoreInternalSelectionEvents) {
        return;
      }

      @Nullable final TreePath[] selectedPaths = getSelectionPaths();

      @NotNull final TreePath[] paths = selectedPaths == null ? EMPTY_PATHS : selectedPaths;
      final List<TraceElement> selectedItems =
        Arrays.stream(paths)
          .map(this::getTopPath)
          .map(myPath2Value::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      fireSelectionChanged(selectedItems);
    });

    setSelectionRow(0);
    expandNodesOnLoad(node -> node == root);
  }

  CollectionTree(@NotNull List<TraceElement> traceElements,
                 @NotNull EvaluationContextImpl evaluationContext) {
    this(traceElements.stream().map(TraceElement::getValue).collect(Collectors.toList()), traceElements, evaluationContext);
  }

  @Override
  public void clearSelection() {
    myIgnoreInternalSelectionEvents = true;
    super.clearSelection();
    myIgnoreInternalSelectionEvents = false;
  }

  @Nullable
  public Rectangle getRectByValue(@NotNull TraceElement element) {
    final TreePath path = myValue2Path.get(element);
    return path == null ? null : getPathBounds(path);
  }

  @Override
  public void highlight(@NotNull List<TraceElement> elements) {
    clearSelection();

    highlightValues(elements);
    tryScrollTo(elements);

    updatePresentation();
  }

  @Override
  public void select(@NotNull List<TraceElement> elements) {
    final TreePath[] paths = elements.stream().map(myValue2Path::get).toArray(TreePath[]::new);

    select(paths);
    highlightValues(elements);

    if (paths.length > 0) {
      scrollPathToVisible(paths[0]);
    }

    updatePresentation();
  }

  @Override
  public void addSelectionListener(@NotNull ValuesSelectionListener listener) {
    // TODO: dispose?
    mySelectionDispatcher.addListener(listener);
  }

  public void addPaintingListener(@NotNull PaintingListener listener) {
    myPaintingDispatcher.addListener(listener);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myPaintingDispatcher.getMulticaster().componentPainted();
  }

  private void select(@NotNull TreePath[] paths) {
    if (myIgnoreExternalSelectionEvents) {
      return;
    }

    myIgnoreInternalSelectionEvents = true;
    getSelectionModel().setSelectionPaths(paths);
    myIgnoreInternalSelectionEvents = false;
  }

  private void fireSelectionChanged(List<TraceElement> selectedItems) {
    myIgnoreExternalSelectionEvents = true;
    mySelectionDispatcher.getMulticaster().selectionChanged(selectedItems);
    myIgnoreExternalSelectionEvents = false;
  }

  private void tryScrollTo(@NotNull List<TraceElement> elements) {
    final int[] rows = elements.stream().map(myValue2Path::get).filter(Objects::nonNull).mapToInt(this::getRowForPath).sorted().toArray();
    if (rows.length == 0) {
      return;
    }

    if (isShowing()) {
      final Rectangle bestVisibleArea = optimizeRowsCountInVisibleRect(rows);
      final Rectangle visibleRect = getVisibleRect();
      final boolean notVisibleHighlightedRowExists = Arrays
        .stream(rows)
        .anyMatch(x -> !visibleRect.intersects(getRowBounds(x)));
      if (notVisibleHighlightedRowExists) {
        scrollRectToVisible(bestVisibleArea);
      }
    }
    else {
      // Use slow path if component hidden
      scrollPathToVisible(getPathForRow(rows[0]));
    }
  }

  @NotNull
  private Rectangle optimizeRowsCountInVisibleRect(@NotNull int[] rows) {
    // a simple scan-line algorithm to find an optimal subset of visible rows (maximum)
    final Rectangle visibleRect = getVisibleRect();
    final int height = visibleRect.height;

    class Result {
      private int top = 0;
      private int bot = 0;

      @Contract(pure = true)
      private int count() {
        return bot - top;
      }
    }

    int topIndex = 0;
    int bottomIndex = 1;
    int topY = getRowBounds(rows[topIndex]).y;

    final Result result = new Result();
    while (bottomIndex < rows.length) {
      final int nextY = getRowBounds(rows[bottomIndex]).y;
      while (nextY - topY > height) {
        topIndex++;
        topY = getRowBounds(rows[topIndex]).y;
      }

      if (bottomIndex - topIndex > result.count()) {
        result.top = topIndex;
        result.bot = bottomIndex;
      }

      bottomIndex++;
    }

    int y = getRowBounds(rows[result.top]).y;
    if (y > visibleRect.y) {
      final Rectangle botBounds = getRowBounds(rows[result.bot]);
      y = botBounds.y + botBounds.height - visibleRect.height;
    }
    return new Rectangle(visibleRect.x, y, visibleRect.width, height);
  }

  private void highlightValues(@NotNull List<TraceElement> elements) {
    myHighlighted = elements.stream().map(myValue2Path::get).collect(Collectors.toSet());
  }

  private void updatePresentation() {
    revalidate();
    repaint();
  }

  public boolean isHighlighted(@NotNull TraceElement traceElement) {
    final TreePath path = myValue2Path.get(traceElement);
    return path != null && (myHighlighted.contains(path) || isPathSelected(path));
  }

  @NotNull
  private static <T> List<T> tail(@NotNull List<T> list) {
    if (list.size() <= 1) {
      return Collections.emptyList();
    }

    return list.subList(1, list.size());
  }

  private class MyRootValue extends XValue {
    private final List<Value> myValues;
    private final EvaluationContextImpl myEvaluationContext;

    MyRootValue(@NotNull List<Value> values, @NotNull EvaluationContextImpl evaluationContext) {
      myValues = values;
      myEvaluationContext = evaluationContext;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      final XValueChildrenList children = new XValueChildrenList();
      for (final Value value : myValues) {
        final PrimitiveValueDescriptor valueDescriptor = new PrimitiveValueDescriptor(myProject, value);
        children.add(new InstanceJavaValue(valueDescriptor, myEvaluationContext, myNodeManager));
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

  @NotNull
  private TreePath getTopPath(@NotNull TreePath path) {
    TreePath current = path;
    while (current != null && !myPath2Value.containsKey(current)) {
      current = current.getParentPath();
    }

    return current != null ? current : path;
  }
}
