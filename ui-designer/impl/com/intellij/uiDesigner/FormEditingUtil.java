package com.intellij.uiDesigner;

import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.Painter;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormEditingUtil {
  private FormEditingUtil() {
  }

  public static boolean canDeleteSelection(final GuiEditor editor){
    final ArrayList<RadComponent> selection = getSelectedComponents(editor);
    return !selection.isEmpty();
  }

  /**
   * <b>This method must be executed in command</b>
   *
   * @param editor the editor in which the selection is deleted.
   */
  public static void deleteSelection(final GuiEditor editor){
    final List<RadComponent> selection = getSelectedComponents(editor);
    deleteComponents(editor, selection, true);
    editor.refreshAndSave(true);
  }

  public static void deleteComponents(final GuiEditor editor, final List<RadComponent> selection, final boolean deleteEmptyCells) {
    final Set<String> deletedComponentIds = new HashSet<String>();
    for (final RadComponent component : selection) {
      boolean wasSelected = component.isSelected();
      final RadContainer parent = component.getParent();

      boolean wasPackedHorz = false;
      boolean wasPackedVert = false;
      if (parent.getParent() != null && parent.getParent().isXY()) {
        final Dimension minSize = parent.getMinimumSize();
        wasPackedHorz = parent.getWidth() == minSize.width;
        wasPackedVert = parent.getHeight() == minSize.height;
      }

      FormEditingUtil.iterate(component, new ComponentVisitor() {
        public boolean visit(final IComponent c) {
          RadComponent rc = (RadComponent) c;
          BindingProperty.checkRemoveUnusedField(rc);
          deletedComponentIds.add(rc.getId());
          return true;
        }
      });


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
      if (delConstraints != null && deleteEmptyCells) {
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

    FormEditingUtil.iterate(editor.getRootContainer(), new ComponentVisitor() {
      public boolean visit(final IComponent component) {
        RadComponent rc = (RadComponent) component;
        for(IProperty p: component.getModifiedProperties()) {
          if (p instanceof IntroComponentProperty) {
            IntroComponentProperty icp = (IntroComponentProperty) p;
            final String value = (String) icp.getValue(rc);
            if (deletedComponentIds.contains(value)) {
              try {
                icp.resetValue(rc);
              }
              catch (Exception e) {
                // ignore
              }
            }
          }
        }
        return true;
      }
    });
  }

  public static void deleteEmptyGridCells(final RadContainer parent, final GridConstraints delConstraints) {
    GridLayoutManager layout = (GridLayoutManager) parent.getLayout();
    for(int row=delConstraints.getRow() + delConstraints.getRowSpan()-1; row >= delConstraints.getRow(); row--) {
      if (row < layout.getRowCount() && GridChangeUtil.isRowEmpty(parent, row)) {
        GridChangeUtil.deleteRow(parent, row);
      }
    }
    for(int col=delConstraints.getColumn() + delConstraints.getColSpan()-1; col >= delConstraints.getColumn(); col--) {
      if (col < layout.getColumnCount() && GridChangeUtil.isColumnEmpty(parent, col)) {
        GridChangeUtil.deleteColumn(parent, col);
      }
    }
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static RadComponent getRadComponentAt(final RadRootContainer rootContainer, final int x, final int y){
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
    return bindingExists(component, binding, null);
  }

  public static boolean bindingExists(IComponent component, final String binding, @Nullable final IComponent exceptComponent) {
    // Check that binding is unique
    final Ref<Boolean> bindingExists = new Ref<Boolean>(Boolean.FALSE);
    iterate(
      component,
      new ComponentVisitor() {
        public boolean visit(final IComponent component) {
          if(component != exceptComponent && binding.equals(component.getBinding())) {
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
  public static RadContainer getRadContainerAt(final RadRootContainer rootContainer, final int x, final int y,
                                               int epsilon) {
    RadComponent component = getRadComponentAt(rootContainer, x, y);
    if (isNullOrRoot(component) && epsilon > 0) {
      // try to find component near specified location
      component = getRadComponentAt(rootContainer, x-epsilon, y-epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x-epsilon, y+epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x+epsilon, y-epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x+epsilon, y+epsilon);
    }

    if (component != null) {
      return component instanceof RadContainer
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

  public static IRootContainer getRoot(IComponent component) {
    while(component != null) {
      if (component.getParentContainer() instanceof IRootContainer) {
        return (IRootContainer) component.getParentContainer();
      }
      component = component.getParentContainer();
    }
    return null;
  }

  /**
   * Iterates component and its children (if any)
   */
  public static void iterate(final IComponent component, final ComponentVisitor visitor){
    iterateImpl(component, visitor);
  }

  public static boolean iterateImpl(final IComponent component, final ComponentVisitor visitor) {
    final boolean shouldContinue;
    try {
      shouldContinue = visitor.visit(component);
    }
    catch (ProcessCanceledException ex) {
      return false;
    }
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

  public static Set<String> collectUsedBundleNames(final IRootContainer rootContainer) {
    final Set<String> bundleNames = new HashSet<String>();
    iterateStringDescriptors(rootContainer, new StringDescriptorVisitor<IComponent>() {
      public boolean visit(final IComponent component, final StringDescriptor descriptor) {
        if (descriptor.getBundleName() != null && !bundleNames.contains(descriptor.getBundleName())) {
          bundleNames.add(descriptor.getBundleName());
        }
        return true;
      }
    });
    return bundleNames;
  }

  public static Locale[] collectUsedLocales(final Module module, final IRootContainer rootContainer) {
    final Set<Locale> locales = new HashSet<Locale>();
    final PropertiesReferenceManager propManager = module.getProject().getComponent(PropertiesReferenceManager.class);
    for(String bundleName: collectUsedBundleNames(rootContainer)) {
      List<PropertiesFile> propFiles = propManager.findPropertiesFiles(module, bundleName.replace('/', '.'));
      for(PropertiesFile propFile: propFiles) {
        locales.add(propFile.getLocale());
      }
    }
    return locales.toArray(new Locale[locales.size()]);
  }

  public static void deleteRowOrColumn(final GuiEditor editor, final RadContainer container,
                                        final int cell, final int orientation) {
    if (!editor.ensureEditable()) {
      return;
    }

    boolean isRow = (orientation == SwingConstants.VERTICAL);
    if (!GridChangeUtil.canDeleteCell(container, cell, isRow, false)) {
      ArrayList<RadComponent> componentsInColumn = new ArrayList<RadComponent>();
      for(RadComponent component: container.getComponents()) {
        GridConstraints c = component.getConstraints();
        if (c.contains(isRow, cell)) {
          componentsInColumn.add(component);
        }
      }

      if (componentsInColumn.size() > 0) {
        String message = isRow
                         ? UIDesignerBundle.message("delete.row.nonempty", componentsInColumn.size())
                         : UIDesignerBundle.message("delete.column.nonempty", componentsInColumn.size());

        final int rc = Messages.showYesNoDialog(editor, message,
                                                isRow ? UIDesignerBundle.message("delete.row.title")
                                                : UIDesignerBundle.message("delete.column.title"), Messages.getQuestionIcon());
        if (rc != DialogWrapper.OK_EXIT_CODE) {
          return;
        }

        deleteComponents(editor, componentsInColumn, false);
      }
    }

    if(SwingConstants.HORIZONTAL == orientation){
      GridChangeUtil.deleteColumn(container, cell);
    }
    else{
      GridChangeUtil.deleteRow(container, cell);
    }
    editor.refreshAndSave(true);
  }

  public static interface StringDescriptorVisitor<T extends IComponent> {
    boolean visit(T component, StringDescriptor descriptor);
  }


  public static void iterateStringDescriptors(final IComponent component,
                                              final StringDescriptorVisitor<IComponent> visitor) {
    iterate(component, new ComponentVisitor<IComponent>() {

      public boolean visit(final IComponent component) {
        for(IProperty prop: component.getModifiedProperties()) {
          Object value = prop.getPropertyValue(component);
          if (value instanceof StringDescriptor) {
            if (!visitor.visit(component, (StringDescriptor) value)) {
              return false;
            }
          }
        }
        if (component.getParentContainer() instanceof ITabbedPane) {
          StringDescriptor tabTitle = ((ITabbedPane) component.getParentContainer()).getTabTitle(component);
          if (tabTitle != null && !visitor.visit(component, tabTitle)) {
            return false;
          }
        }
        if (component instanceof IContainer) {
          final StringDescriptor borderTitle = ((IContainer) component).getBorderTitle();
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
   * @return the found component.
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

  public static interface ComponentVisitor <Type extends IComponent>{
    /**
     * @return true if iteration should continue
     */
    boolean visit(Type component);
  }
}
