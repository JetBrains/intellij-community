// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.ui.impl;

import com.intellij.debugger.streams.core.trace.*;
import com.intellij.debugger.streams.core.ui.PaintingListener;
import com.intellij.debugger.streams.core.ui.TraceContainer;
import com.intellij.debugger.streams.core.ui.ValuesSelectionListener;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ui.tree.TreeUtil.collectSelectedPaths;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class CollectionTree extends XDebuggerTree implements TraceContainer {
  private static final Map<Integer, Color> COLORS_CACHE = new HashMap<>();

  protected final Map<TraceElement, TreePath> myValue2Path = new HashMap<>();
  protected final Map<TreePath, TraceElement> myPath2Value = new HashMap<>();

  @SuppressWarnings("unused")
  private final String myDebugName;

  private Set<TreePath> myHighlighted = Collections.emptySet();
  private final EventDispatcher<ValuesSelectionListener> mySelectionDispatcher = EventDispatcher.create(ValuesSelectionListener.class);
  private final EventDispatcher<PaintingListener> myPaintingDispatcher = EventDispatcher.create(PaintingListener.class);

  private boolean myIgnoreInternalSelectionEvents = false;
  private boolean myIgnoreExternalSelectionEvents = false;

  protected CollectionTree(@NotNull List<TraceElement> traceElements,
                 @NotNull GenericEvaluationContext context,
                 @NotNull CollectionTreeBuilder collectionTreeBuilder,
                 @NotNull String debugName) {
    super(context.getProject(), collectionTreeBuilder.getEditorsProvider(), null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, null);

    myDebugName = debugName;

    addTreeSelectionListener(e -> {
      if (myIgnoreInternalSelectionEvents) {
        return;
      }
      final List<TraceElement> selectedItems =
        collectSelectedPaths(this).stream()
          .map(this::getTopPath)
          .map(myPath2Value::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      fireSelectionChanged(selectedItems);
    });

    setSelectionRow(0);
    expandNodesOnLoad(node -> node == getRoot());
  }

  public static CollectionTree create(@Nullable Value streamResult,
                                      @NotNull List<TraceElement> traceElements,
                                      @NotNull DebuggerCommandLauncher debuggerCommandLauncher,
                                      @NotNull GenericEvaluationContext evaluationContext,
                                      @NotNull CollectionTreeBuilder collectionTreeBuilder,
                                      @NotNull String debugName) {
    if (streamResult == null) {
      return new IntermediateTree(traceElements, evaluationContext, collectionTreeBuilder, debugName);
    }
    else {
      return new TerminationTree(streamResult, traceElements, debuggerCommandLauncher, evaluationContext, collectionTreeBuilder, debugName);
    }
  }

  @Override
  public boolean isFileColorsEnabled() {
    return true;
  }

  @Override
  public @Nullable Color getFileColorForPath(@NotNull TreePath path) {
    if (isPathHighlighted(path)) {
      final Color background = UIUtil.getTreeSelectionBackground(true);
      return COLORS_CACHE.computeIfAbsent(background.getRGB(), rgb -> new JBColor(
        new Color(background.getRed(), background.getGreen(), background.getBlue(), 75),
        new Color(background.getRed(), background.getGreen(), background.getBlue(), 100)));
    }

    return UIUtil.getTreeBackground();
  }

  @Override
  public void clearSelection() {
    myIgnoreInternalSelectionEvents = true;
    super.clearSelection();
    myIgnoreInternalSelectionEvents = false;
  }

  public @Nullable Rectangle getRectByValue(@NotNull TraceElement element) {
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

  @Override
  public boolean highlightedExists() {
    return !isSelectionEmpty() || !myHighlighted.isEmpty();
  }

  public abstract int getItemsCount();

  public void addPaintingListener(@NotNull PaintingListener listener) {
    myPaintingDispatcher.addListener(listener);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myPaintingDispatcher.getMulticaster().componentPainted();
  }

  private void select(TreePath @NotNull [] paths) {
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

  private @NotNull Rectangle optimizeRowsCountInVisibleRect(int @NotNull [] rows) {
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
    Rectangle rowBounds = getRowBounds(rows[topIndex]);
    if (rowBounds == null) return visibleRect;
    int topY = rowBounds.y;

    final Result result = new Result();
    while (bottomIndex < rows.length) {
      final int nextY = getRowBounds(rows[bottomIndex]).y;
      while (nextY - topY > height) {
        topIndex++;
        rowBounds = getRowBounds(rows[topIndex]);
        if (rowBounds == null) return visibleRect;
        topY = rowBounds.y;
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
    return path != null && isPathHighlighted(path);
  }

  private boolean isPathHighlighted(@NotNull TreePath path) {
    return myHighlighted.contains(path) || isPathSelected(path);
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
