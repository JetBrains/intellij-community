/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightColors;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.util.ui.PlatformColors;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Painter {
  /**
   * This color is used to paint decoration of non selected components
   */
  private static final Color NON_SELECTED_BOUNDARY_COLOR = new Color(114, 126, 143);
  /**
   * This color is used to paint decoration of selected components
   */
  private static final Color SELECTED_BOUNDARY_COLOR = new Color(8, 8, 108);

  private static final Color HIGHLIGHTED_BOUNDARY_COLOR = Color.RED;

  /**
   * This color is used to paint grid cell for selected container
   */
  static final Color SELECTED_GRID_COLOR = new Color(47, 67, 96);
  /**
   * This color is used to paint grid cell for non selected container
   */
  static final Color NON_SELECTED_GRID_COLOR = new Color(130, 140, 155);

  public final static int WEST_MASK = 1;
  public final static int EAST_MASK = 2;
  public final static int NORTH_MASK = 4;
  public final static int SOUTH_MASK = 8;
  private final static int R = 2;
  private final static int GAP = R;
  private static final int NW = 0;
  private static final int N = 1;
  private static final int NE = 2;
  private static final int E = 3;
  private static final int SE = 4;
  private static final int S = 5;
  private static final int SW = 6;
  private static final int W = 7;

  private Painter() {
  }

  public static void paintComponentDecoration(final GuiEditor editor, final RadComponent component, final Graphics g) {
    // Collect selected components and paint decoration for non selected components
    final ArrayList<RadComponent> selection = new ArrayList<>();
    final Rectangle layeredPaneRect = editor.getLayeredPane().getVisibleRect();
    FormEditingUtil.iterate(
      component,
      new FormEditingUtil.ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if (!component.getDelegee().isShowing()) { // Skip invisible components
            return true;
          }
          final Shape oldClip = g.getClip();
          final RadContainer parent = component.getParent();
          if (parent != null) {
            final Point p = SwingUtilities.convertPoint(component.getDelegee(), 0, 0, editor.getLayeredPane());
            final Rectangle visibleRect = layeredPaneRect.intersection(new Rectangle(p.x, p.y, parent.getWidth(), parent.getHeight()));
            g.setClip(visibleRect);
          }
          if (component.isSelected()) { // we will paint selection later
            selection.add(component);
          }
          else {
            paintComponentBoundsImpl(editor, component, g);
          }
          paintGridOutline(editor, component, g);
          if (parent != null) {
            g.setClip(oldClip);
          }
          return true;
        }
      }
    );

    // Let's paint decoration for selected components
    for (int i = selection.size() - 1; i >= 0; i--) {
      final Shape oldClip = g.getClip();
      final RadComponent c = selection.get(i);
      final RadContainer parent = c.getParent();
      if (parent != null) {
        final Point p = SwingUtilities.convertPoint(c.getDelegee(), 0, 0, editor.getLayeredPane());
        final Rectangle visibleRect = layeredPaneRect.intersection(new Rectangle(p.x, p.y, parent.getWidth(), parent.getHeight()));
        g.setClip(visibleRect);
      }
      paintComponentBoundsImpl(editor, c, g);
      if (parent != null) {
        g.setClip(oldClip);
      }
    }
  }

  /**
   * Paints container border. For grids the method also paints vertical and
   * horizontal lines that indicate bounds of the rows and columns.
   * Method does nothing if the <code>component</code> is not an instance
   * of <code>RadContainer</code>.
   */
  private static void paintComponentBoundsImpl(final GuiEditor editor, @NotNull final RadComponent component, final Graphics g) {
    if (!(component instanceof RadContainer) && !(component instanceof RadNestedForm) && !component.isDragBorder()) {
      return;
    }

    boolean highlightBoundaries = (getDesignTimeInsets(component) > 2);

    if (component instanceof RadContainer && !component.isDragBorder()) {
      RadContainer container = (RadContainer)component;
      if (!highlightBoundaries && (container.getBorderTitle() != null || container.getBorderType() != BorderType.NONE)) {
        return;
      }
    }
    final Point point = SwingUtilities.convertPoint(
      component.getDelegee(),
      0,
      0,
      editor.getRootContainer().getDelegee()
    );
    g.translate(point.x, point.y);
    try {
      if (component.isDragBorder()) {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setColor(LightColors.YELLOW);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.translate(1, 1);
      }
      else if (highlightBoundaries) {
        g.setColor(HIGHLIGHTED_BOUNDARY_COLOR);
      }
      else if (component.isSelected()) {
        g.setColor(SELECTED_BOUNDARY_COLOR);
      }
      else {
        g.setColor(NON_SELECTED_BOUNDARY_COLOR);
      }
      g.drawRect(0, 0, component.getWidth() - 1, component.getHeight() - 1);
      if (component.isDragBorder()) {
        g.translate(-1, -1);
      }
    }
    finally {
      g.translate(-point.x, -point.y);
    }
  }

  private static int getDesignTimeInsets(RadComponent component) {
    while (component != null) {
      Integer designTimeInsets = (Integer)component.getDelegee().getClientProperty(GridLayoutManager.DESIGN_TIME_INSETS);
      if (designTimeInsets != null) {
        return designTimeInsets.intValue();
      }
      component = component.getParent();
    }
    return 0;
  }

  /**
   * This method paints grid bounds for "grid" containers
   */
  public static void paintGridOutline(final GuiEditor editor, @NotNull final RadComponent component, final Graphics g) {
    if (!editor.isShowGrid()) {
      return;
    }
    if (!(component instanceof RadContainer)) {
      return;
    }
    final RadContainer container = (RadContainer)component;
    if (!container.getLayoutManager().isGrid()) {
      return;
    }

    // performance: don't paint grid outline in drag layer
    Container parent = component.getDelegee().getParent();
    while (parent != null) {
      if (parent == editor.getDragLayer()) {
        return;
      }
      parent = parent.getParent();
    }

    final Point point = SwingUtilities.convertPoint(
      component.getDelegee(),
      0,
      0,
      editor.getRootContainer().getDelegee()
    );
    g.translate(point.x, point.y);
    try {
      // Paint grid
      if (container.getWidth() > 0 && container.getHeight() > 0) {
        Image gridImage = CachedGridImage.getGridImage(container);
        g.drawImage(gridImage, 0, 0, null);
      }
    }
    finally {
      g.translate(-point.x, -point.y);
    }
  }

  /**
   * Paints selection for the specified <code>component</code>.
   */
  public static void paintSelectionDecoration(@NotNull RadComponent component, Graphics g,
                                              boolean focused) {
    if (component.isSelected()) {
      if (focused) {
        g.setColor(PlatformColors.BLUE);
      }
      else {
        g.setColor(Color.GRAY);
      }
      final Point[] points = getPoints(component.getWidth(), component.getHeight());
      for (final Point point : points) {
        g.fillRect(point.x - R, point.y - R, 2 * R + 1, 2 * R + 1);
      }
    }
    else if (component.getWidth() < FormEditingUtil.EMPTY_COMPONENT_SIZE || component.getHeight() < FormEditingUtil.EMPTY_COMPONENT_SIZE) {
      Graphics2D g2d = (Graphics2D)g;
      Composite oldComposite = g2d.getComposite();
      Stroke oldStroke = g2d.getStroke();
      Color oldColor = g2d.getColor();

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f));
      g2d.setStroke(new BasicStroke(0.7f));
      g2d.setColor(Color.black);
      g2d.drawRect(0, 0, Math.max(component.getWidth(), FormEditingUtil.EMPTY_COMPONENT_SIZE),
                   Math.max(component.getHeight(), FormEditingUtil.EMPTY_COMPONENT_SIZE));

      g2d.setComposite(oldComposite);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
    }
  }

  /**
   * @param x in component's coord system
   * @param y in component's coord system
   */
  public static int getResizeMask(@NotNull final RadComponent component, final int x, final int y) {
    if (component.getParent() == null || !component.isSelected()) {
      return 0;
    }

    // only components in XY can be resized...
    /*
    if (!component.getParent().isXY()) {
      return 0;
    }
    */

    final int width = component.getWidth();
    final int height = component.getHeight();

    final Point[] points = getPoints(width, height);

    if (isInside(x, y, points[SE])) {
      return EAST_MASK | SOUTH_MASK;
    }
    else if (isInside(x, y, points[NW])) {
      return WEST_MASK | NORTH_MASK;
    }
    else if (isInside(x, y, points[N])) {
      return NORTH_MASK;
    }
    else if (isInside(x, y, points[NE])) {
      return EAST_MASK | NORTH_MASK;
    }
    else if (isInside(x, y, points[W])) {
      return WEST_MASK;
    }
    else if (isInside(x, y, points[E])) {
      return EAST_MASK;
    }
    else if (isInside(x, y, points[SW])) {
      return WEST_MASK | SOUTH_MASK;
    }
    else if (isInside(x, y, points[S])) {
      return SOUTH_MASK;
    }
    else {
      return 0;
    }
  }

  private static boolean isInside(final int x, final int y, final Point r) {
    return x >= r.x - R && x <= r.x + R && y >= r.y - R && y <= r.y + R;
  }

  @JdkConstants.CursorType
  public static int getResizeCursor(final int resizeMask) {
    if (resizeMask == (WEST_MASK | NORTH_MASK)) {
      return Cursor.NW_RESIZE_CURSOR;
    }
    else if (resizeMask == NORTH_MASK) {
      return Cursor.N_RESIZE_CURSOR;
    }
    else if (resizeMask == (EAST_MASK | NORTH_MASK)) {
      return Cursor.NE_RESIZE_CURSOR;
    }
    else if (resizeMask == WEST_MASK) {
      return Cursor.W_RESIZE_CURSOR;
    }
    else if (resizeMask == EAST_MASK) {
      return Cursor.E_RESIZE_CURSOR;
    }
    else if (resizeMask == (WEST_MASK | SOUTH_MASK)) {
      return Cursor.SW_RESIZE_CURSOR;
    }
    else if (resizeMask == SOUTH_MASK) {
      return Cursor.S_RESIZE_CURSOR;
    }
    else if (resizeMask == (EAST_MASK | SOUTH_MASK)) {
      return Cursor.SE_RESIZE_CURSOR;
    }
    else {
      throw new IllegalArgumentException("unknown resizeMask: " + resizeMask);
    }
  }

  public static Point[] getPoints(final int width, final int height) {
    final Point[] points = new Point[8];

    points[NW] = new Point(GAP, GAP); // NW
    points[N] = new Point(width / 2, GAP); // N
    points[NE] = new Point(width - GAP - 1, GAP); // NE
    points[E] = new Point(width - GAP - 1, height / 2); // E
    points[SE] = new Point(width - GAP - 1, height - GAP - 1); // SE
    points[S] = new Point(width / 2, height - GAP - 1); // S
    points[SW] = new Point(GAP, height - GAP - 1); // SW
    points[W] = new Point(GAP, height / 2); // W

    return points;
  }

  public static void paintButtonGroupLines(RadRootContainer rootContainer, RadButtonGroup group, Graphics g) {
    List<RadComponent> components = rootContainer.getGroupContents(group);
    if (components.size() < 2) return;
    Rectangle[] allBounds = new Rectangle[components.size()];
    int lastTop = -1;
    int minLeft = Integer.MAX_VALUE;
    for (int i = 0; i < components.size(); i++) {
      final Rectangle rc = SwingUtilities.convertRectangle(
        components.get(i).getParent().getDelegee(),
        components.get(i).getBounds(),
        rootContainer.getDelegee()
      );
      allBounds[i] = rc;

      minLeft = Math.min(minLeft, rc.x);
      if (i == 0) {
        lastTop = rc.y;
      }
      else if (lastTop != rc.y) {
        lastTop = Integer.MIN_VALUE;
      }
    }

    Graphics2D g2d = (Graphics2D)g;
    Stroke oldStroke = g2d.getStroke();
    g2d.setStroke(new BasicStroke(2.0f));
    g2d.setColor(new Color(104, 107, 130));
    if (lastTop != Integer.MIN_VALUE) {
      // all items in group have same Y
      int left = Integer.MAX_VALUE;
      int right = Integer.MIN_VALUE;
      for (Rectangle rc : allBounds) {
        final int midX = (int)rc.getCenterX();
        left = Math.min(left, midX);
        right = Math.max(right, midX);
        g2d.drawLine(midX, lastTop - 8, midX, lastTop);
      }
      g2d.drawLine(left, lastTop - 8, right, lastTop - 8);
    }
    else {
      int top = Integer.MAX_VALUE;
      int bottom = Integer.MIN_VALUE;
      for (Rectangle rc : allBounds) {
        final int midY = (int)rc.getCenterY();
        top = Math.min(top, midY);
        bottom = Math.max(bottom, midY);
        g2d.drawLine(minLeft - 8, midY, rc.x, midY);
      }
      g2d.drawLine(minLeft - 8, top, minLeft - 8, bottom);
    }
    g2d.setStroke(oldStroke);
  }

  public static void paintComponentTag(final RadComponent component, final Graphics g) {
    if (component instanceof RadContainer) return;
    for (IProperty prop : component.getModifiedProperties()) {
      if (prop.getName().equals(SwingProperties.TEXT)) {
        final Object desc = prop.getPropertyValue(component);
        if (!(desc instanceof StringDescriptor) || ((StringDescriptor)desc).getValue() == null ||
            ((StringDescriptor)desc).getValue().length() > 0) {
          return;
        }
      }
      else if (prop.getName().equals(SwingProperties.MODEL)) {
        // don't paint tags on non-empty lists
        final Object value = prop.getPropertyValue(component);
        if (value instanceof String[] && ((String[])value).length > 0) {
          return;
        }
      }
    }

    Rectangle bounds = component.getDelegee().getBounds();
    if (bounds.width > 100 && bounds.height > 40) {
      StringBuilder tagBuilder = new StringBuilder();
      if (component.getBinding() != null) {
        tagBuilder.append(component.getBinding()).append(':');
      }
      String className = component.getComponentClassName();
      int pos = className.lastIndexOf('.');
      if (pos >= 0) {
        tagBuilder.append(className.substring(pos + 1));
      }
      else {
        tagBuilder.append(className);
      }
      final Rectangle2D stringBounds = g.getFontMetrics().getStringBounds(tagBuilder.toString(), g);
      Graphics2D g2d = (Graphics2D)g;
      g2d.setColor(PlatformColors.BLUE);
      g2d.fillRect(0, 0, (int)stringBounds.getWidth(), (int)stringBounds.getHeight());
      g2d.setColor(JBColor.WHITE);
      UISettings.setupAntialiasing(g);
      g.drawString(tagBuilder.toString(), 0, g.getFontMetrics().getAscent());
    }
  }
}
