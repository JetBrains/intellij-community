package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.Painter;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.palette.ComponentItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormEditingUtil {
  private static final Logger LOG=Logger.getInstance("#com.intellij.uiDesigner.FormEditingUtil");

  private FormEditingUtil() {
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
    for (final RadComponent component : selection) {
      boolean wasSelected = component.isSelected();
      final RadContainer parent = component.getParent();

      boolean wasPackedHorz = false;
      boolean wasPackedVert = false;
      if (parent.getParent() != null && parent.getParent().isXY()) {
        final Dimension minSize = parent.getMinimumSize();
        wasPackedHorz = (parent.getWidth() == minSize.width);
        wasPackedVert = (parent.getHeight() == minSize.height);
      }

      GridConstraints delConstraints = parent.isGrid() ? component.getConstraints() : null;

      int index = parent.indexOfComponent(component);
      parent.removeComponent(component);
      if (wasSelected) {
        if (parent.getComponentCount() > index) {
          parent.getComponent(index).setSelected(true);
        }
        else if (index > 0 && parent.getComponentCount() == index) {
          parent.getComponent(index-1).setSelected(true);
        }
        else {
          parent.setSelected(true);
        }
      }
      if (delConstraints != null) {
        deleteEmptyGridCells(parent, delConstraints);
      }

      if (wasPackedHorz || wasPackedVert) {
        final Dimension minSize = parent.getMinimumSize();
        Dimension newSize = new Dimension(parent.getWidth(), parent.getHeight());
        if (wasPackedHorz) {
          newSize.width = minSize.width;
        }
        if (wasPackedVert) {
          newSize.height = minSize.height;
        }
        parent.setSize(newSize);
      }
    }

    editor.refreshAndSave(true);
  }

  public static void deleteEmptyGridCells(final RadContainer parent, final GridConstraints delConstraints) {
    for(int row=delConstraints.getRow() + delConstraints.getRowSpan()-1; row >= delConstraints.getRow(); row--) {
      if (GridChangeUtil.isRowEmpty(parent, row)) {
        GridChangeUtil.deleteRow(parent, row);
      }
    }
    for(int col=delConstraints.getColumn() + delConstraints.getColSpan()-1; col >= delConstraints.getColumn(); col--) {
      if (GridChangeUtil.isColumnEmpty(parent, col)) {
        GridChangeUtil.deleteColumn(parent, col);
      }
    }
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
  @Nullable
  public static RadComponent getDraggerHost(@NotNull final GuiEditor editor){
    final Ref<RadComponent> result = new Ref<RadComponent>();
    iterate(
      editor.getRootContainer(),
      new ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if(component.hasDragger()){
            result.set(component);
            return false;
          }
          return true;
        }
      }
    );

    return result.get();
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static Cursor getDropCursor(final GuiEditor editor, final int x, final int y, final int componentCount){
    if (canDrop(editor, x, y, componentCount)) {
      return getMoveDropCursor();
    }
    return getMoveNoDropCursor();
  }

  public static Cursor getMoveDropCursor() {
    try {
      //noinspection HardCodedStringLiteral
      return Cursor.getSystemCustomCursor("MoveDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  public static Cursor getMoveNoDropCursor() {
    try {
      //noinspection HardCodedStringLiteral
      return Cursor.getSystemCustomCursor("MoveNoDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  public static Cursor getCopyDropCursor() {
    try {
      //noinspection HardCodedStringLiteral
      return Cursor.getSystemCustomCursor("CopyDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static boolean canDrop(final GuiEditor editor, final int x, final int y, final int componentCount){
    if (componentCount == 0) {
      return false;
    }

    final RadComponent componentAt = getRadContainerAt(editor, x, y, 0);
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
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("cannot drop");
    }

    final RadContainer targetContainer = getRadContainerAt(editor, x, y, 0);
    assert targetContainer != null;
    final Point targetPoint = SwingUtilities.convertPoint(editor.getDragLayer(), x, y, targetContainer.getDelegee());
    return targetContainer.drop(targetPoint.x, targetPoint.y, components, dx, dy);
  }

  /**
   * @return currently selected components. Method returns the minimal amount of
   * selected component which contains all selected components. It means that if the
   * selected container contains some selected children then only this container
   * will be added to the returned array.
   */
  @NotNull
  public static ArrayList<RadComponent> getSelectedComponents(@NotNull final GuiEditor editor){
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
  @NotNull
  public static ArrayList<RadComponent> getAllSelectedComponents(@NotNull final GuiEditor editor){
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
      message = message != null? UIDesignerBundle.message("error.class.not.found.N", message) : UIDesignerBundle.message("error.class.not.found");
    }
    else if (ex instanceof NoClassDefFoundError) {
      message = message != null? UIDesignerBundle.message("error.required.class.not.found.N", message) : UIDesignerBundle.message("error.required.class.not.found");
    }
    return message;
  }

  public static boolean bindingExists(IComponent component, final String binding) {
    // Check that binding is unique
    final Ref<Boolean> bindingExists = new Ref<Boolean>(Boolean.FALSE);
    FormEditingUtil.iterate(
      component,
      new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          if(binding.equals(component.getBinding())){
            bindingExists.set(Boolean.TRUE);
            return false;
          }
          return true;
        }
      }
    );

    return bindingExists.get().booleanValue();
  }

  @Nullable
  public static RadContainer getRadContainerAt(final GuiEditor editor, final int x, final int y,
                                               int epsilon) {
    RadComponent component = getRadComponentAt(editor, x, y);
    if (isNullOrRoot(component) && epsilon > 0) {
      // try to find component near specified location
      component = getRadComponentAt(editor, x-epsilon, y-epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(editor, x-epsilon, y+epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(editor, x+epsilon, y-epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(editor, x+epsilon, y+epsilon);
    }

    if (component != null) {
      return (component instanceof RadContainer)
             ? (RadContainer)component
             : component.getParent();
    }
    return null;
  }

  private static boolean isNullOrRoot(final RadComponent component) {
    return component == null || component instanceof RadRootContainer;
  }

  public static GridConstraints getDefaultConstraints(final RadComponent component) {
    final Palette palette = Palette.getInstance(component.getModule().getProject());
    final ComponentItem item = palette.getItem(component.getComponentClassName());
    if (item != null) {
      final GridConstraints defaultConstraints = item.getDefaultConstraints();
      return defaultConstraints;
    }
    return new GridConstraints();
  }

  public static interface ComponentVisitor <Type extends IComponent>{
    /**
     * @return true if iteration should continue
     */
    boolean visit(Type component);
  }

  public static interface StringDescriptorVisitor<T extends IComponent> {
    boolean visit(T component, StringDescriptor descriptor);
  }

  /**
   * Iterates component and its children (if any)
   */
  public static void iterate(@NotNull final IComponent component, @NotNull final ComponentVisitor visitor){
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

  public static void iterateStringDescriptors(final LwComponent component,
                                              final StringDescriptorVisitor<LwComponent> visitor) {
    iterate(component, new ComponentVisitor<LwComponent>() {

      public boolean visit(final LwComponent component) {
        LwIntrospectedProperty[] props = component.getAssignedIntrospectedProperties();
        for(LwIntrospectedProperty prop: props) {
          if (prop instanceof LwRbIntroStringProperty) {
            StringDescriptor descriptor = (StringDescriptor) component.getPropertyValue(prop);
            if (!visitor.visit(component, descriptor)) {
              return false;
            }
          }
        }
        if (component.getParent() instanceof LwTabbedPane) {
          LwTabbedPane.Constraints constraints = (LwTabbedPane.Constraints) component.getCustomLayoutConstraints();
          if (constraints != null && !visitor.visit(component, constraints.myTitle)) {
            return false;
          }
        }
        if (component instanceof LwContainer) {
          final StringDescriptor borderTitle = ((LwContainer) component).getBorderTitle();
          if (borderTitle != null && !visitor.visit(component, borderTitle)) {
            return false;
          }
        }
        return true;
      }
    });
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
  @Nullable
  public static RadComponent findComponent(@NotNull final RadComponent component, @NotNull final String id) {
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

  @Nullable
  public static RadComponent findComponentAnywhere(@NotNull final RadComponent component, @NotNull final String valueId) {
    RadContainer container = component.getParent();
    if (container == null) {
      return null;
    }
    while (container.getParent() != null) {
      container = container.getParent();
    }
    return findComponent(container, valueId);
  }

  @Nullable
  public static PsiClass findClassToBind(@NotNull final Module module, @NotNull final String classToBindName) {
    return PsiManager.getInstance(module.getProject()).findClass(
      classToBindName.replace('$','.'),
      GlobalSearchScope.moduleScope(module)
    );
  }
}
