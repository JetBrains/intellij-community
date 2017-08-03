/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Couple;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.shared.XYLayoutManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author yole
 */
public class GridBuildUtil {
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.GridBuildUtil");

  private final static int HORIZONTAL_GRID = 1;
  private final static int VERTICAL_GRID = 2;
  private final static int GRID = 3;
  /**
   * TODO[anton,vova]: most likely should be equal to "xy grid step" when available
   */
  private static final int GRID_TREMOR = 5;

  private GridBuildUtil() {
  }

  public static void breakGrid(final GuiEditor editor) {
    final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
    if (selection.size() != 1){
      return;
    }
    if (!(selection.get(0) instanceof RadContainer)) {
      return;
    }
    final RadContainer container = (RadContainer)selection.get(0);
    if (
      container instanceof RadScrollPane ||
      container instanceof RadSplitPane ||
      container instanceof RadTabbedPane
    ){
      return;
    }

    final RadContainer parent = container.getParent();

    if (parent instanceof RadRootContainer) {
      editor.getRootContainer().setMainComponentBinding(container.getBinding());
    }

    // XY can be broken only if its parent is also XY.
    // In other words, breaking of XY is a deletion of unnecessary intermediate panel
    if (container.isXY() && !parent.isXY()) {
      return;
    }

    if (parent != null && parent.isXY()) {
      // parent is XY
      // put the contents of the container into 'parent' and remove 'container'

      final int dx = container.getX();
      final int dy = container.getY();

      while (container.getComponentCount() > 0) {
        final RadComponent component = container.getComponent(0);
        component.shift(dx, dy);
        parent.addComponent(component);
      }

      parent.removeComponent(container);
    }
    else {
      // container becomes XY
      final XYLayoutManager xyLayout = new XYLayoutManagerImpl();
      container.setLayout(xyLayout);
      xyLayout.setPreferredSize(container.getSize());
    }

    editor.refreshAndSave(true);
  }

  public static void convertToVerticalGrid(final GuiEditor editor){
    convertToGridImpl(editor, VERTICAL_GRID);
  }

  public static void convertToHorizontalGrid(final GuiEditor editor){
    convertToGridImpl(editor, HORIZONTAL_GRID);
  }

  public static void convertToGrid(final GuiEditor editor){
    convertToGridImpl(editor, GRID);
  }

  private static void convertToGridImpl(final GuiEditor editor, final int gridType) {
    final boolean createNewContainer;

    final RadContainer parent;
    final RadComponent[] componentsToConvert;
    {
      final ArrayList<RadComponent> selection = FormEditingUtil.getSelectedComponents(editor);
      if (selection.size() == 0) {
        // root container selected
        final RadRootContainer rootContainer = editor.getRootContainer();
        if (rootContainer.getComponentCount() < 2) {
          // nothing to convert
          return;
        }

        componentsToConvert = new RadComponent[rootContainer.getComponentCount()];
        for (int i = 0; i < componentsToConvert.length; i++) {
          componentsToConvert[i] = rootContainer.getComponent(i);
        }
        parent = rootContainer;
        createNewContainer = true;
      }
      else if (selection.size() == 1 && selection.get(0) instanceof RadContainer) {
        parent = (RadContainer)selection.get(0);
        componentsToConvert = new RadComponent[parent.getComponentCount()];
        for (int i = 0; i < componentsToConvert.length; i++) {
          componentsToConvert[i] = parent.getComponent(i);
        }
        createNewContainer = false;
      }
      else {
        componentsToConvert = selection.toArray(new RadComponent[selection.size()]);
        parent = selection.get(0).getParent();
        createNewContainer = true;
      }
    }

    if (!parent.isXY()) {
      // only components in XY can be layed out in grid
      return;
    }
    for (int i = 1; i < componentsToConvert.length; i++) {
      final RadComponent component = componentsToConvert[i];
      if (component.getParent() != parent) {
        return;
      }
    }

    final GridLayoutManager gridLayoutManager;
    if (componentsToConvert.length == 0) {
      // we convert empty XY panel to grid
      gridLayoutManager = new GridLayoutManager(1,1);
    }
    else {
      if (gridType == VERTICAL_GRID) {
        gridLayoutManager = createOneDimensionGrid(componentsToConvert, true);
      }
      else if (gridType == HORIZONTAL_GRID) {
        gridLayoutManager = createOneDimensionGrid(componentsToConvert, false);
      }
      else if (gridType == GRID) {
        gridLayoutManager = createTwoDimensionGrid(componentsToConvert);
      }
      else {
        throw new IllegalArgumentException("invalid grid type: " + gridType);
      }
    }

    for (final RadComponent component : componentsToConvert) {
      if (component instanceof RadContainer) {
        final LayoutManager layout = ((RadContainer)component).getLayout();
        if (layout instanceof XYLayoutManager) {
          ((XYLayoutManager)layout).setPreferredSize(component.getSize());
        }
      }
    }

    if (createNewContainer) {
      // we should create a new panel

      final Module module = editor.getModule();
      final ComponentItem panelItem = Palette.getInstance(editor.getProject()).getPanelItem();
      final RadContainer newContainer = new RadContainer(editor, FormEditingUtil.generateId(editor.getRootContainer()));
      newContainer.setLayout(gridLayoutManager);
      newContainer.init(editor, panelItem);

      for (RadComponent componentToConvert : componentsToConvert) {
        newContainer.addComponent(componentToConvert);
      }

      final Point topLeftPoint = getTopLeftPoint(componentsToConvert);
      newContainer.setLocation(topLeftPoint);

      final Point bottomRightPoint = getBottomRightPoint(componentsToConvert);
      final Dimension size = new Dimension(bottomRightPoint.x - topLeftPoint.x, bottomRightPoint.y - topLeftPoint.y);
      Util.adjustSize(newContainer.getDelegee(), newContainer.getConstraints(), size);
      newContainer.getDelegee().setSize(size);

      parent.addComponent(newContainer);

      FormEditingUtil.clearSelection(editor.getRootContainer());
      newContainer.setSelected(true);

      // restore binding of main component
      {
        final String mainComponentBinding = editor.getRootContainer().getMainComponentBinding();
        if (mainComponentBinding != null && parent instanceof RadRootContainer) {
          newContainer.setBinding(mainComponentBinding);
          editor.getRootContainer().setMainComponentBinding(null);
        }
      }
    }
    else {
      // convert entire 'parent' to grid

      parent.setLayout(gridLayoutManager);

      FormEditingUtil.clearSelection(editor.getRootContainer());
      parent.setSelected(true);
    }

    editor.refreshAndSave(true);
  }

  private static GridLayoutManager createOneDimensionGrid(final RadComponent[] selection, final boolean isVertical){
    Arrays.sort(
      selection,
      (o1, o2) -> {
        final Rectangle bounds1 = o1.getBounds();
        final Rectangle bounds2 = o2.getBounds();

        if (isVertical) {
          return (bounds1.y + bounds1.height / 2) - (bounds2.y + bounds2.height / 2);
        }
        else {
          return (bounds1.x + bounds1.width / 2) - (bounds2.x + bounds2.width / 2);
        }
      }
    );

    for (int i = 0; i < selection.length; i++) {
      final RadComponent component = selection[i];
      final GridConstraints constraints = component.getConstraints();
      if (isVertical) {
        constraints.setRow(i);
        constraints.setColumn(0);
      }
      else {
        constraints.setRow(0);
        constraints.setColumn(i);
      }
      constraints.setRowSpan(1);
      constraints.setColSpan(1);
    }

    final GridLayoutManager gridLayoutManager;
    if (isVertical) {
      gridLayoutManager = new GridLayoutManager(selection.length, 1);
    }
    else {
      gridLayoutManager = new GridLayoutManager(1, selection.length);
    }
    return gridLayoutManager;
  }

  /**
   * @param x array of {@code X} coordinates of components that should be layed out in a grid.
   * This is input/output parameter.
   *
   * @param y array of {@code Y} coordinates of components that should be layed out in a grid.
   * This is input/output parameter.
   *
   * @param rowSpans output parameter.
   *
   * @param colSpans output parameter.
   *
   * @return pair that says how many (rows, columns) are in the composed grid.
   */
  public static Couple<Integer> layoutInGrid(
    final int[] x,
    final int[] y,
    final int[] rowSpans,
    final int[] colSpans
  ){
    LOG.assertTrue(x.length == y.length);
    LOG.assertTrue(y.length == colSpans.length);
    LOG.assertTrue(colSpans.length == rowSpans.length);

    for (int i = 0; i < x.length; i++) {
      colSpans[i] = Math.max(colSpans[i], 1);
      rowSpans[i] = Math.max(rowSpans[i], 1);

      if (colSpans[i] > GRID_TREMOR * 4) {
        colSpans[i] -= GRID_TREMOR * 2;
        x[i] += GRID_TREMOR;
      }
      if (rowSpans[i] > GRID_TREMOR * 4) {
        rowSpans[i] -= GRID_TREMOR * 2;
        y[i] += GRID_TREMOR;
      }
    }


    return Couple.of(
      new Integer(Util.eliminate(y, rowSpans, null)),
      new Integer(Util.eliminate(x, colSpans, null))
    );
  }

  private static GridLayoutManager createTwoDimensionGrid(final RadComponent[] selection){
    final int[] x = new int[selection.length];
    final int[] y = new int[selection.length];
    final int[] colSpans = new int[selection.length];
    final int[] rowSpans = new int[selection.length];

    for (int i = selection.length - 1; i >= 0; i--) {
      x[i] = selection[i].getX();
      y[i] = selection[i].getY();
      rowSpans[i] = selection[i].getHeight();
      colSpans[i] = selection[i].getWidth();
    }

    final Couple<Integer> pair = layoutInGrid(x, y, rowSpans, colSpans);
    for (int i = 0; i < selection.length; i++) {
      final RadComponent component = selection[i];
      final GridConstraints constraints = component.getConstraints();

      constraints.setRow(y[i]);
      constraints.setRowSpan(rowSpans[i]);

      constraints.setColumn(x[i]);
      constraints.setColSpan(colSpans[i]);
    }

    return new GridLayoutManager(pair.first.intValue(), pair.second.intValue());
  }

  /**
   * Find top left point of component group, i.e. (minimum X of all components; minimum Y of all components)
   * @param components array should contain at least one element
   */
  private static Point getTopLeftPoint(final RadComponent[] components){
    LOG.assertTrue(components.length > 0);

    final Point point = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
    for (final RadComponent component : components) {
      point.x = Math.min(component.getX(), point.x);
      point.y = Math.min(component.getY(), point.y);
    }

    return point;
  }

  /**
   * Find bottom right point of component group, i.e. (maximum (x + width) of all components; maximum (y + height) of all components)
   * @param components array should contain at least one element
   */
  private static Point getBottomRightPoint(final RadComponent[] components){
    LOG.assertTrue(components.length > 0);

    final Point point = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
    for (final RadComponent component : components) {
      point.x = Math.max(component.getX() + component.getWidth(), point.x);
      point.y = Math.max(component.getY() + component.getHeight(), point.y);
    }

    return point;
  }
}
