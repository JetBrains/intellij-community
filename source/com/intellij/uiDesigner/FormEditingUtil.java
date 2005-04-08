package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.shared.XYLayoutManager;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormEditingUtil {
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.FormEditingUtil");

  private final static int HORIZONTAL_GRID = 1;
  private final static int VERTICAL_GRID = 2;
  private final static int GRID = 3;

  /**
   * TODO[anton,vova]: most likely should be equal to "xy grid step" when available
   */
  private static final int GRID_TREMOR = 5;

  public static void breakGrid(final GuiEditor editor) {
    final ArrayList<RadComponent> selection = getSelectedComponents(editor);
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
      final ArrayList<RadComponent> selection = getSelectedComponents(editor);
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

    for (int i = 0; i < componentsToConvert.length; i++) {
      final RadComponent component = componentsToConvert[i];
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
      final RadContainer newContainer = new RadContainer(module, editor.generateId());
      newContainer.setLayout(gridLayoutManager);
      newContainer.init(panelItem);

      for (int i = 0; i < componentsToConvert.length; i++) {
        newContainer.addComponent(componentsToConvert[i]);
      }

      final Point topLeftPoint = getTopLeftPoint(componentsToConvert);
      newContainer.setLocation(topLeftPoint);

      final Point bottomRightPoint = getBottomRightPoint(componentsToConvert);
      final Dimension size = new Dimension(bottomRightPoint.x - topLeftPoint.x, bottomRightPoint.y - topLeftPoint.y);
      Util.adjustSize(newContainer.getDelegee(), newContainer.getConstraints(), size);
      newContainer.getDelegee().setSize(size);

      parent.addComponent(newContainer);

      clearSelection(editor.getRootContainer());
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

      clearSelection(editor.getRootContainer());
      parent.setSelected(true);
    }

    editor.refreshAndSave(true);
  }

  private static GridLayoutManager createOneDimensionGrid(final RadComponent[] selection, final boolean isVertical){
    Arrays.sort(
      selection,
      new Comparator() {
        public int compare(final Object o1, final Object o2){
          final Rectangle bounds1 = ((RadComponent)o1).getBounds();
          final Rectangle bounds2 = ((RadComponent)o2).getBounds();

          if (isVertical) {
            return (bounds1.y + bounds1.height / 2) - (bounds2.y + bounds2.height / 2);
          }
          else {
            return (bounds1.x + bounds1.width / 2) - (bounds2.x + bounds2.width / 2);
          }
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
   * @param x array of <code>X</code> coordinates of components that should be layed out in a grid.
   * This is input/output parameter.
   *
   * @param y array of <code>Y</code> coordinates of components that should be layed out in a grid.
   * This is input/output parameter.
   *
   * @param rowSpans output parameter.
   *
   * @param colSpans output parameter.
   *
   * @return pair that says how many (rows, columns) are in the composed grid.
   */
  public static Pair<Integer, Integer> layoutInGrid(
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


    return new Pair<Integer, Integer>(
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

    final Pair<Integer, Integer> pair = layoutInGrid(x, y, rowSpans, colSpans);
    for (int i = 0; i < selection.length; i++) {
      final RadComponent component = selection[i];
      final GridConstraints constraints = component.getConstraints();

      constraints.setRow(y[i]);
      constraints.setRowSpan(rowSpans[i]);

      constraints.setColumn(x[i]);
      constraints.setColSpan(colSpans[i]);
    }
    final GridLayoutManager gridLayoutManager = new GridLayoutManager(pair.first.intValue(), pair.second.intValue());

    return gridLayoutManager;
  }

  public static boolean canDeleteSelection(final GuiEditor editor){
    final ArrayList<RadComponent> selection = getSelectedComponents(editor);
    return !selection.isEmpty();
  }

  /**
   * <b>This method must be executed in command</b>
   */ 
  public static void deleteSelection(final GuiEditor editor){
    final ArrayList<RadComponent> selection = getSelectedComponents(editor);
    for (int i = 0; i < selection.size(); i++) {
      final RadComponent component = selection.get(i);
      component.getParent().removeComponent(component);
    }

    editor.refreshAndSave(true);
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static RadComponent getRadComponentAt(final GuiEditor editor, final int x, final int y){
    final RadContainer rootContainer = editor.getRootContainer();
    Component c = SwingUtilities.getDeepestComponentAt(rootContainer.getDelegee(), x, y);

    RadComponent result = null;

    while (c != null) {
      if (c instanceof JComponent){
        final RadComponent component = (RadComponent)((JComponent)c).getClientProperty(RadComponent.CLIENT_PROP_RAD_COMPONENT);
        if (component != null) {

          if (result == null) {
            result = component;
          }
          else {
            final Point p = SwingUtilities.convertPoint(rootContainer.getDelegee(), x, y, c);
            if (Painter.getResizeMask(component, p.x, p.y) != 0) {
              result = component;
            }
          }
        }
      }
      c = c.getParent();
    }

    return result;
  }

  /**
   * @return component which has dragger. There is only one component with the dargger
   * at a time.
   */
  public static RadComponent getDraggerHost(final GuiEditor editor){
    LOG.assertTrue(editor != null);

    final RadComponent[] result = new RadComponent[1];
    iterate(
      editor.getRootContainer(),
      new ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if(component.hasDragger()){
            result[0] = component;
            return false;
          }
          return true;
        }
      }
    );

    return result[0];
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static Cursor getDropCursor(final GuiEditor editor, final int x, final int y, final int componentCount){
    Cursor cursor = Cursor.getDefaultCursor();
    if (canDrop(editor, x, y, componentCount)) {
      try {
        cursor = Cursor.getSystemCustomCursor("MoveDrop.32x32");
      }
      catch (Exception ex) {
      }
    }
    else {
      try {
        cursor = Cursor.getSystemCustomCursor("MoveNoDrop.32x32");
      }
      catch (Exception ex) {
      }
    }
    return cursor;
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static boolean canDrop(final GuiEditor editor, final int x, final int y, final int componentCount){
    if (componentCount == 0) {
      return false;
    }

    final RadComponent componentAt = getRadComponentAt(editor, x, y);
    if (componentAt == null) {
      return false;
    }

    final Point targetPoint = SwingUtilities.convertPoint(editor.getDragLayer(), x, y, componentAt.getDelegee());
    return componentAt.canDrop(targetPoint.x, targetPoint.y, componentCount);
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   * @param dx x coordinate of components relative to x
   * @param dx shift of component relative to x
   * @param dx shift of component relative to y
   */
  public static DropInfo drop(final GuiEditor editor, final int x, final int y, final RadComponent[] components, final int[] dx, final int[] dy){
    if (!canDrop(editor, x, y, components.length)) {
      throw new IllegalArgumentException("cannot drop");
    }

    final RadContainer targetContainer = (RadContainer)getRadComponentAt(editor, x, y);
    final Point targetPoint = SwingUtilities.convertPoint(editor.getDragLayer(), x, y, targetContainer.getDelegee());
    return targetContainer.drop(targetPoint.x, targetPoint.y, components, dx, dy);
  }

  /**
   * @return currently selected components. Method returns the minimal amount of
   * selected component which contains all selected components. It means that if the
   * selected container contains some selected children then only this container
   * will be added to the returned array.
   */
  public static ArrayList<RadComponent> getSelectedComponents(final GuiEditor editor){
    LOG.assertTrue(editor != null);

    final ArrayList<RadComponent> result = new ArrayList<RadComponent>();
    calcSelectedComponentsImpl(result, editor.getRootContainer());
    return result;
  }

  private static void calcSelectedComponentsImpl(final ArrayList<RadComponent> result, final RadContainer container){
    if (container.isSelected()) {
      if (container.getParent() != null) { // ignore RadRootContainer
        result.add(container);
        return;
      }
    }

    for (int i = 0; i < container.getComponentCount(); i++) {
      final RadComponent component = container.getComponent(i);
      if (component instanceof RadContainer) {
        calcSelectedComponentsImpl(result, (RadContainer)component);
      }
      else {
        if (component.isSelected()) {
          result.add(component);
        }
      }
    }
  }

  /**
   * @return all selected component inside the <code>editor</code>
   */
  public static ArrayList<RadComponent> getAllSelectedComponents(final GuiEditor editor){
    LOG.assertTrue(editor != null);

    final ArrayList<RadComponent> result = new ArrayList<RadComponent>();
    iterate(
      editor.getRootContainer(),
      new ComponentVisitor<RadComponent>(){
        public boolean visit(final RadComponent component) {
          if(component.isSelected()){
            result.add(component);
          }
          return true;
        }
      }
    );
    return result;
  }

  public static String getExceptionMessage(Throwable ex) {
    if (ex instanceof RuntimeException) {
      final Throwable cause = ex.getCause();
      if (cause != null && cause != ex) {
        return getExceptionMessage(cause);
      }
    }
    else
    if (ex instanceof InvocationTargetException) {
      final Throwable target = ((InvocationTargetException)ex).getTargetException();
      if (target != null && target != ex) {
        return getExceptionMessage(target);
      }
    }
    String message = ex.getMessage();
    if (ex instanceof ClassNotFoundException) {
      message = message != null? "Class not found: " + message : "Class not found";
    }
    else if (ex instanceof NoClassDefFoundError) {
      message = message != null? "Required class not found: " + message : "Required class not found";
    }
    return message;
  }

  public static interface ComponentVisitor <Type extends IComponent>{
    /**
     * @return true if iteration should continue
     */ 
    public boolean visit(Type component);
  }

  /**
   * Iterates component and its children (if any)
   */
  public static void iterate(final IComponent component, final ComponentVisitor visitor){
    LOG.assertTrue(component != null);
    LOG.assertTrue(visitor != null);

    iterateImpl(component, visitor);
  }

  
  private static boolean iterateImpl(final IComponent component, final ComponentVisitor visitor) {
    final boolean shouldContinue = visitor.visit(component);
    if (!shouldContinue) {
      return false;
    }

    if (!(component instanceof IContainer)) {
      return true;
    }
    
    final IContainer container = (IContainer)component;
    
    for (int i = 0; i < container.getComponentCount(); i++) {
      final IComponent c = container.getComponent(i);
      if (!iterateImpl(c, visitor)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Find top left point of component group, i.e. (minimum X of all components; minimum Y of all components) 
   * @param components array should contain at least one element
   */
  private static Point getTopLeftPoint(final RadComponent[] components){
    LOG.assertTrue(components.length > 0);

    final Point point = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
    for (int i = 0; i < components.length; i++) {
      final RadComponent component = components[i];
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
    for (int i = 0; i < components.length; i++) {
      final RadComponent component = components[i];
      point.x = Math.max(component.getX() + component.getWidth(), point.x);
      point.y = Math.max(component.getY() + component.getHeight(), point.y);
    }

    return point;
  }

  public static void clearSelection(final RadContainer container){
    container.setSelected(false);

    for (int i = 0; i < container.getComponentCount(); i++) {
      final RadComponent c = container.getComponent(i);
      if (c instanceof RadContainer) {
        clearSelection((RadContainer)c);
      }
      else {
        c.setSelected(false);
      }
    }
  }

  /**
   * Finds component with the specified <code>id</code> starting from the
   * <code>container</code>. The method goes recursively through the hierarchy
   * of components. Note, that if <code>container</code> itself has <code>id</code>
   * then the method immediately retuns it.
   */
  public static RadComponent findComponent(final RadComponent component, final String id) {
    LOG.assertTrue(component != null);
    LOG.assertTrue(id != null);

    if (id.equals(component.getId())) {
      return component;
    }
    if (!(component instanceof RadContainer)) {
      return null;
    }

    final RadContainer uiContainer = (RadContainer)component;
    for (int i=0; i < uiContainer.getComponentCount(); i++){
      final RadComponent found = findComponent(uiContainer.getComponent(i), id);
      if (found != null){
        return found;
      }
    }
    return null;
  }

  public static PsiClass findClassToBind(final Module module, final String classToBindName) {
    LOG.assertTrue(module != null);
    LOG.assertTrue(classToBindName != null);

    return PsiManager.getInstance(module.getProject()).findClass(
      classToBindName.replace('$','.'),
      GlobalSearchScope.moduleScope(module)
    );
  }
}
