// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.ui.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;

/**
 * @author Vitaliy.Bibaev
 */
public class TraceTreeCellRenderer extends ColoredTreeCellRenderer {
  private static final Logger LOG = Logger.getInstance(TraceTreeCellRenderer.class);

  private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
  private boolean myHaveLink;
  private int myLinkOffset;
  private int myLinkWidth;

  private final MyLongTextHyperlink myLongTextLink = new MyLongTextHyperlink();

  TraceTreeCellRenderer() {
    getIpad().right = 0;
    myLink.getIpad().left = 0;
  }

  public void customizeCellRenderer(@NotNull final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myHaveLink = false;
    myLink.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.appendToComponent(this);
    setIcon(node.getIcon());

    Rectangle treeVisibleRect = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewRect() : tree.getVisibleRect();
    TreePath path = tree.getPathForRow(row);
    int rowX = path != null ? getRowX((BasicTreeUI)tree.getUI(), row, path.getPathCount() - 1) : 0;

    if (myHaveLink) {
      setupLinkDimensions(treeVisibleRect, rowX);
    }
    else {
      int visibleRectRightX = treeVisibleRect.x + treeVisibleRect.width;
      int notFittingWidth = rowX + super.getPreferredSize().width - visibleRectRightX;
      if (node instanceof XValueNodeImpl && notFittingWidth > 0) {
        // text does not fit visible area - show link
        String rawValue = DebuggerUIUtil.getNodeRawValue((XValueNodeImpl)node);
        if (!StringUtil.isEmpty(rawValue) && tree.isShowing()) {
          Point treeRightSideOnScreen = new Point(visibleRectRightX, 0);
          SwingUtilities.convertPointToScreen(treeRightSideOnScreen, tree);
          Rectangle screen = ScreenUtil.getScreenRectangle(treeRightSideOnScreen);
          // text may fit the screen in ExpandableItemsHandler
          if (screen.x + screen.width < treeRightSideOnScreen.x + notFittingWidth) {
            myLongTextLink.setupComponent(rawValue, ((XDebuggerTree)tree).getProject());
            append(myLongTextLink.getLinkText(), myLongTextLink.getTextAttributes(), myLongTextLink);
            setupLinkDimensions(treeVisibleRect, rowX);
            myLinkWidth = 0;
          }
        }
      }
    }
    putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myHaveLink);
  }

  private static Method ourGetRowXMethod = null;

  private static int getRowX(BasicTreeUI ui, int row, int depth) {
    if (ourGetRowXMethod == null) {
      try {
        ourGetRowXMethod = BasicTreeUI.class.getDeclaredMethod("getRowX", int.class, int.class);
        ourGetRowXMethod.setAccessible(true);
      }
      catch (NoSuchMethodException e) {
        LOG.error(e);
      }
    }
    if (ourGetRowXMethod != null) {
      try {
        return (Integer)ourGetRowXMethod.invoke(ui, row, depth);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return 0;
  }

  private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
    Dimension linkSize = myLink.getPreferredSize();
    myLinkWidth = linkSize.width;
    myLinkOffset = Math.min(super.getPreferredSize().width, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
    if (tag instanceof XDebuggerTreeNodeHyperlink && ((XDebuggerTreeNodeHyperlink)tag).alwaysOnScreen()) {
      myHaveLink = true;
      myLink.append(fragment, attributes, tag);
    }
    else {
      super.append(fragment, attributes, tag);
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (myHaveLink) {
      Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, g.getClipBounds().height);
      try {
        super.doPaint(textGraphics);
      }
      finally {
        textGraphics.dispose();
      }
      g.translate(myLinkOffset, 0);
      myLink.setHeight(getHeight());
      myLink.doPaint(g);
      g.translate(-myLinkOffset, 0);
    }
    else {
      super.doPaint(g);
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myHaveLink) {
      size.width += myLinkWidth;
    }
    return size;
  }

  @Nullable
  @Override
  public Object getFragmentTagAt(int x) {
    if (myHaveLink) {
      return myLink.getFragmentTagAt(x - myLinkOffset);
    }
    return super.getFragmentTagAt(x);
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    private int myHeight;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {}

    @SuppressWarnings("EmptyMethod")
    protected void doPaint(Graphics2D g) {
      super.doPaint(g);
    }

    void setHeight(int height) {
      myHeight = height;
    }

    @Override
    public int getHeight() {
      return myHeight;
    }
  }

  private static class MyLongTextHyperlink extends XDebuggerTreeNodeHyperlink {
    private String myText;
    private Project myProject;

    MyLongTextHyperlink() {
      super(XDebuggerBundle.message("node.test.show.full.value"));
    }

    void setupComponent(String text, Project project) {
      myText = text;
      myProject = project;
    }

    @Override
    public boolean alwaysOnScreen() {
      return true;
    }

    @Override
    public void onClick(MouseEvent event) {
      DebuggerUIUtil.showValuePopup(new ImmediateFullValueEvaluator(myText), event, myProject, null);
      event.consume();
    }
  }
}
