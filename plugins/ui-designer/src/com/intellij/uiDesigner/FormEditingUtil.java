/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.ComponentDropLocation;
import com.intellij.uiDesigner.designSurface.DraggedComponentList;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.designSurface.Painter;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.radComponents.RadAbstractGridLayoutManager;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FormEditingUtil {
  private FormEditingUtil() {
  }

  public static boolean canDeleteSelection(final GuiEditor editor) {
    final ArrayList<RadComponent> selection = getSelectedComponents(editor);
    if (selection.isEmpty()) return false;
    final RadRootContainer rootContainer = editor.getRootContainer();
    if (rootContainer.getComponentCount() > 0 && selection.contains(rootContainer.getComponent(0))) {
      return false;
    }
    return true;
  }

  /**
   * <b>This method must be executed in command</b>
   *
   * @param editor the editor in which the selection is deleted.
   */
  public static void deleteSelection(final GuiEditor editor) {
    final List<RadComponent> selection = getSelectedComponents(editor);
    deleteComponents(selection, true);
    editor.refreshAndSave(true);
  }

  public static void deleteComponents(final Collection<? extends RadComponent> selection, boolean deleteEmptyCells) {
    if (selection.size() == 0) {
      return;
    }
    final RadRootContainer rootContainer = (RadRootContainer)getRoot(selection.iterator().next());
    final Set<String> deletedComponentIds = new HashSet<>();
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

      iterate(component, new ComponentVisitor() {
        public boolean visit(final IComponent c) {
          RadComponent rc = (RadComponent)c;
          BindingProperty.checkRemoveUnusedField(rootContainer, rc.getBinding(), null);
          deletedComponentIds.add(rc.getId());
          return true;
        }
      });


      GridConstraints delConstraints = parent.getLayoutManager().isGrid() ? component.getConstraints() : null;

      int index = parent.indexOfComponent(component);
      parent.removeComponent(component);
      if (wasSelected) {
        if (parent.getComponentCount() > index) {
          parent.getComponent(index).setSelected(true);
        }
        else if (index > 0 && parent.getComponentCount() == index) {
          parent.getComponent(index - 1).setSelected(true);
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

    iterate(rootContainer, new ComponentVisitor() {
      public boolean visit(final IComponent component) {
        RadComponent rc = (RadComponent)component;
        for (IProperty p : component.getModifiedProperties()) {
          if (p instanceof IntroComponentProperty) {
            IntroComponentProperty icp = (IntroComponentProperty)p;
            final String value = icp.getValue(rc);
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
    deleteEmptyGridCells(parent, delConstraints, true);
    deleteEmptyGridCells(parent, delConstraints, false);
  }

  private static void deleteEmptyGridCells(final RadContainer parent, final GridConstraints delConstraints, final boolean isRow) {
    final RadAbstractGridLayoutManager layoutManager = parent.getGridLayoutManager();
    for (int cell = delConstraints.getCell(isRow) + delConstraints.getSpan(isRow) - 1; cell >= delConstraints.getCell(isRow); cell--) {
      if (cell < parent.getGridCellCount(isRow) && GridChangeUtil.canDeleteCell(parent, cell, isRow) == GridChangeUtil.CellStatus.Empty &&
          !layoutManager.isGapCell(parent, isRow, cell)) {
        layoutManager.deleteGridCells(parent, cell, isRow);
      }
    }
  }

  public static final int EMPTY_COMPONENT_SIZE = 5;

  private static Component getDeepestEmptyComponentAt(JComponent parent, Point location) {
    int size = parent.getComponentCount();

    for (int i = 0; i < size; i++) {
      Component child = parent.getComponent(i);

      if (child.isShowing()) {
        if (child.getWidth() < EMPTY_COMPONENT_SIZE || child.getHeight() < EMPTY_COMPONENT_SIZE) {
          Point childLocation = child.getLocationOnScreen();
          Rectangle bounds = new Rectangle();

          bounds.x = childLocation.x;
          bounds.y = childLocation.y;
          bounds.width = child.getWidth();
          bounds.height = child.getHeight();
          bounds.grow(child.getWidth() < EMPTY_COMPONENT_SIZE ? EMPTY_COMPONENT_SIZE : 0,
                      child.getHeight() < EMPTY_COMPONENT_SIZE ? EMPTY_COMPONENT_SIZE : 0);

          if (bounds.contains(location)) {
            return child;
          }
        }

        if (child instanceof JComponent) {
          Component result = getDeepestEmptyComponentAt((JComponent)child, location);

          if (result != null) {
            return result;
          }
        }
      }
    }

    return null;
  }

  /**
   * @param x in editor pane coordinates
   * @param y in editor pane coordinates
   */
  public static RadComponent getRadComponentAt(final RadRootContainer rootContainer, final int x, final int y) {
    Point location = new Point(x, y);
    SwingUtilities.convertPointToScreen(location, rootContainer.getDelegee());
    Component c = getDeepestEmptyComponentAt(rootContainer.getDelegee(), location);

    if (c == null) {
      c = SwingUtilities.getDeepestComponentAt(rootContainer.getDelegee(), x, y);
    }

    RadComponent result = null;

    while (c != null) {
      if (c instanceof JComponent) {
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
   *         at a time.
   */
  @Nullable
  public static RadComponent getDraggerHost(@NotNull final GuiEditor editor) {
    final Ref<RadComponent> result = new Ref<>();
    iterate(
      editor.getRootContainer(),
      new ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if (component.hasDragger()) {
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
      return Cursor.getSystemCustomCursor("MoveDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  public static Cursor getMoveNoDropCursor() {
    try {
      return Cursor.getSystemCustomCursor("MoveNoDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  public static Cursor getCopyDropCursor() {
    try {
      return Cursor.getSystemCustomCursor("CopyDrop.32x32");
    }
    catch (Exception ex) {
      return Cursor.getDefaultCursor();
    }
  }

  /**
   * @return currently selected components. Method returns the minimal amount of
   *         selected component which contains all selected components. It means that if the
   *         selected container contains some selected children then only this container
   *         will be added to the returned array.
   */
  @NotNull
  public static ArrayList<RadComponent> getSelectedComponents(@NotNull final GuiEditor editor) {
    final ArrayList<RadComponent> result = new ArrayList<>();
    calcSelectedComponentsImpl(result, editor.getRootContainer());
    return result;
  }

  private static void calcSelectedComponentsImpl(final ArrayList<RadComponent> result, final RadContainer container) {
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
  public static ArrayList<RadComponent> getAllSelectedComponents(@NotNull final GuiEditor editor) {
    final ArrayList<RadComponent> result = new ArrayList<>();
    iterate(
      editor.getRootContainer(),
      new ComponentVisitor<RadComponent>() {
        public boolean visit(final RadComponent component) {
          if (component.isSelected()) {
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
    else if (ex instanceof InvocationTargetException) {
      final Throwable target = ((InvocationTargetException)ex).getTargetException();
      if (target != null && target != ex) {
        return getExceptionMessage(target);
      }
    }
    String message = ex.getMessage();
    if (ex instanceof ClassNotFoundException) {
      message =
        message != null ? UIDesignerBundle.message("error.class.not.found.N", message) : UIDesignerBundle.message("error.class.not.found");
    }
    else if (ex instanceof NoClassDefFoundError) {
      message = message != null
                ? UIDesignerBundle.message("error.required.class.not.found.N", message)
                : UIDesignerBundle.message("error.required.class.not.found");
    }
    return message;
  }

  public static IComponent findComponentWithBinding(IComponent component, final String binding) {
    return findComponentWithBinding(component, binding, null);
  }

  public static IComponent findComponentWithBinding(IComponent component,
                                                    final String binding,
                                                    @Nullable final IComponent exceptComponent) {
    // Check that binding is unique
    final Ref<IComponent> boundComponent = new Ref<>();
    iterate(
      component,
      new ComponentVisitor() {
        public boolean visit(final IComponent component) {
          if (component != exceptComponent && binding.equals(component.getBinding())) {
            boundComponent.set(component);
            return false;
          }
          return true;
        }
      }
    );

    return boundComponent.get();
  }

  @Nullable
  public static RadContainer getRadContainerAt(final RadRootContainer rootContainer, final int x, final int y,
                                               int epsilon) {
    RadComponent component = getRadComponentAt(rootContainer, x, y);
    if (isNullOrRoot(component) && epsilon > 0) {
      // try to find component near specified location
      component = getRadComponentAt(rootContainer, x - epsilon, y - epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x - epsilon, y + epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x + epsilon, y - epsilon);
      if (isNullOrRoot(component)) component = getRadComponentAt(rootContainer, x + epsilon, y + epsilon);
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
    final Palette palette = Palette.getInstance(component.getProject());
    final ComponentItem item = palette.getItem(component.getComponentClassName());
    if (item != null) {
      return item.getDefaultConstraints();
    }
    return new GridConstraints();
  }

  public static IRootContainer getRoot(IComponent component) {
    while (component != null) {
      if (component.getParentContainer() instanceof IRootContainer) {
        return (IRootContainer)component.getParentContainer();
      }
      component = component.getParentContainer();
    }
    return null;
  }

  /**
   * Iterates component and its children (if any)
   */
  public static void iterate(final IComponent component, final ComponentVisitor visitor) {
    iterateImpl(component, visitor);
  }

  private static boolean iterateImpl(final IComponent component, final ComponentVisitor visitor) {
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
    final Set<String> bundleNames = new HashSet<>();
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
    final Set<Locale> locales = new HashSet<>();
    final PropertiesReferenceManager propManager = PropertiesReferenceManager.getInstance(module.getProject());
    for (String bundleName : collectUsedBundleNames(rootContainer)) {
      List<PropertiesFile> propFiles = propManager.findPropertiesFiles(module, bundleName.replace('/', '.'));
      for (PropertiesFile propFile : propFiles) {
        locales.add(propFile.getLocale());
      }
    }
    return locales.toArray(new Locale[locales.size()]);
  }

  public static void deleteRowOrColumn(final GuiEditor editor, final RadContainer container,
                                       final int[] cellsToDelete, final boolean isRow) {
    Arrays.sort(cellsToDelete);
    final int[] cells = ArrayUtil.reverseArray(cellsToDelete);
    if (!editor.ensureEditable()) {
      return;
    }

    Runnable runnable = () -> {
      if (!GridChangeUtil.canDeleteCells(container, cells, isRow)) {
        Set<RadComponent> componentsInColumn = new HashSet<>();
        for (RadComponent component : container.getComponents()) {
          GridConstraints c = component.getConstraints();
          for (int cell : cells) {
            if (c.contains(isRow, cell)) {
              componentsInColumn.add(component);
              break;
            }
          }
        }

        if (componentsInColumn.size() > 0) {
          String message = isRow
                           ? UIDesignerBundle.message("delete.row.nonempty", componentsInColumn.size(), cells.length)
                           : UIDesignerBundle.message("delete.column.nonempty", componentsInColumn.size(), cells.length);

          final int rc = Messages.showYesNoDialog(editor, message,
                                                  isRow ? UIDesignerBundle.message("delete.row.title")
                                                        : UIDesignerBundle.message("delete.column.title"), Messages.getQuestionIcon());
          if (rc != Messages.YES) {
            return;
          }

          deleteComponents(componentsInColumn, false);
        }
      }

      for (int cell : cells) {
        container.getGridLayoutManager().deleteGridCells(container, cell, isRow);
      }
      editor.refreshAndSave(true);
    };
    CommandProcessor.getInstance().executeCommand(editor.getProject(), runnable,
                                                  isRow ? UIDesignerBundle.message("command.delete.row")
                                                        : UIDesignerBundle.message("command.delete.column"), null);
  }

  /**
   * @param rootContainer
   * @return id
   */
  public static String generateId(final RadRootContainer rootContainer) {
    while (true) {
      final String id = Integer.toString((int)(Math.random() * 1024 * 1024), 16);
      if (findComponent(rootContainer, id) == null) {
        return id;
      }
    }
  }

  /**
   * @return {@link com.intellij.uiDesigner.designSurface.GuiEditor} from the context. Can be <code>null</code>.
   */
  @Nullable
  public static GuiEditor getEditorFromContext(@NotNull final DataContext context) {
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (editor instanceof UIFormEditor) {
      return ((UIFormEditor)editor).getEditor();
    }
    else {
      return GuiEditor.DATA_KEY.getData(context);
    }
  }

  @Nullable
  public static GuiEditor getActiveEditor(final DataContext context) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }
    final DesignerToolWindowManager toolWindowManager = DesignerToolWindowManager.getInstance(project);
    if (toolWindowManager == null) {
      return null;
    }
    return toolWindowManager.getActiveFormEditor();
  }

  /**
   * @param componentToAssignBinding
   * @param binding
   * @param component                topmost container where to find duplicate binding. In most cases
   *                                 it should be {@link com.intellij.uiDesigner.designSurface.GuiEditor#getRootContainer()}
   */
  public static boolean isBindingUnique(
    final IComponent componentToAssignBinding,
    final String binding,
    final IComponent component
  ) {
    return findComponentWithBinding(component, binding, componentToAssignBinding) == null;
  }

  @Nullable
  public static String buildResourceName(final PsiFile file) {
    PsiDirectory directory = file.getContainingDirectory();
    if (directory != null) {
      PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
      String packageName = pkg != null ? pkg.getQualifiedName() : "";
      if (packageName.length() == 0) {
        return file.getName();
      }
      return packageName.replace('.', '/') + '/' + file.getName();
    }
    return null;
  }

  @Nullable
  public static RadContainer getSelectionParent(final List<RadComponent> selection) {
    RadContainer parent = null;
    for (RadComponent c : selection) {
      if (parent == null) {
        parent = c.getParent();
      }
      else if (parent != c.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  public static Rectangle getSelectionBounds(List<RadComponent> selection) {
    int minRow = Integer.MAX_VALUE;
    int minCol = Integer.MAX_VALUE;
    int maxRow = 0;
    int maxCol = 0;

    for (RadComponent c : selection) {
      minRow = Math.min(minRow, c.getConstraints().getRow());
      minCol = Math.min(minCol, c.getConstraints().getColumn());
      maxRow = Math.max(maxRow, c.getConstraints().getRow() + c.getConstraints().getRowSpan());
      maxCol = Math.max(maxCol, c.getConstraints().getColumn() + c.getConstraints().getColSpan());
    }
    return new Rectangle(minCol, minRow, maxCol - minCol, maxRow - minRow);
  }

  public static boolean isComponentSwitchedInView(@NotNull RadComponent component) {
    while (component.getParent() != null) {
      if (!component.getParent().getLayoutManager().isSwitchedToChild(component.getParent(), component)) {
        return false;
      }
      component = component.getParent();
    }
    return true;
  }

  /**
   * Selects the component and ensures that the tabbed panes containing the component are
   * switched to the correct tab.
   *
   * @param editor
   * @param component the component to select. @return true if the component is enclosed in at least one tabbed pane, false otherwise.
   */
  public static boolean selectComponent(final GuiEditor editor, @NotNull final RadComponent component) {
    boolean hasTab = false;
    RadComponent parent = component;
    while (parent.getParent() != null) {
      if (parent.getParent().getLayoutManager().switchContainerToChild(parent.getParent(), parent)) {
        hasTab = true;
      }
      parent = parent.getParent();
    }
    component.setSelected(true);
    editor.setSelectionLead(component);
    return hasTab;
  }

  public static void selectSingleComponent(final GuiEditor editor, final RadComponent component) {
    final RadContainer root = (RadContainer)getRoot(component);
    if (root == null) return;

    ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
    // this can return null if the click to select the control also requested to grab the focus -
    // the component tree will be instantiated after the event has been processed completely
    if (builder != null) {
      builder.beginUpdateSelection();
    }
    try {
      clearSelection(root);
      selectComponent(editor, component);
      editor.setSelectionAnchor(component);
      editor.scrollComponentInView(component);
    }
    finally {
      if (builder != null) {
        builder.endUpdateSelection();
      }
    }
  }

  public static void selectComponents(final GuiEditor editor, List<RadComponent> components) {
    if (components.size() > 0) {
      RadComponent component = components.get(0);
      ComponentTreeBuilder builder = DesignerToolWindowManager.getInstance(editor).getComponentTreeBuilder();
      if (builder == null) {
        // race condition when handling event?
        return;
      }
      builder.beginUpdateSelection();
      try {
        clearSelection((RadContainer)getRoot(component));
        for (RadComponent aComponent : components) {
          selectComponent(editor, aComponent);
        }
      }
      finally {
        builder.endUpdateSelection();
      }
    }
  }

  public static boolean isDropOnChild(final DraggedComponentList draggedComponentList,
                                      final ComponentDropLocation location) {
    if (location.getContainer() == null) {
      return false;
    }

    for (RadComponent component : draggedComponentList.getComponents()) {
      if (isChild(location.getContainer(), component)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isChild(RadContainer maybeChild, RadComponent maybeParent) {
    while (maybeChild != null) {
      if (maybeParent == maybeChild) {
        return true;
      }
      maybeChild = maybeChild.getParent();
    }
    return false;
  }

  public static PsiMethod findCreateComponentsMethod(final PsiClass aClass) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    PsiMethod method;
    try {
      method = factory.createMethodFromText("void " + AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME + "() {}",
                                            aClass);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
    return aClass.findMethodBySignature(method, true);
  }

  public static Object getNextSaveUndoGroupId(final Project project) {
    final GuiEditor guiEditor = DesignerToolWindowManager.getInstance(project).getActiveFormEditor();
    return guiEditor == null ? null : guiEditor.getNextSaveGroupId();
  }

  public static int adjustForGap(final RadContainer container, final int cellIndex, final boolean isRow, final int delta) {
    if (container.getGridLayoutManager().isGapCell(container, isRow, cellIndex)) {
      return cellIndex + delta;
    }
    return cellIndex;
  }

  public static int prevRow(final RadContainer container, final int row) {
    return adjustForGap(container, row - 1, true, -1);
  }

  public static int nextRow(final RadContainer container, final int row) {
    return adjustForGap(container, row + 1, true, 1);
  }

  public static int prevCol(final RadContainer container, final int col) {
    return adjustForGap(container, col - 1, false, -1);
  }

  public static int nextCol(final RadContainer container, final int col) {
    return adjustForGap(container, col + 1, false, 1);
  }

  @Nullable
  public static IButtonGroup findGroupForComponent(final IRootContainer radRootContainer, @NotNull final IComponent component) {
    for (IButtonGroup group : radRootContainer.getButtonGroups()) {
      for (String id : group.getComponentIds()) {
        if (component.getId().equals(id)) {
          return group;
        }
      }
    }
    return null;
  }

  public static void remapToActionTargets(final List<RadComponent> selection) {
    for (int i = 0; i < selection.size(); i++) {
      final RadComponent c = selection.get(i);
      if (c.getParent() != null) {
        selection.set(i, c.getParent().getActionTargetComponent(c));
      }
    }
  }

  public static void showPopupUnderComponent(final JBPopup popup, final RadComponent selectedComponent) {
    // popup.showUnderneathOf() doesn't work on invisible components
    Rectangle rc = selectedComponent.getBounds();
    Point pnt = new Point(rc.x, rc.y + rc.height);
    popup.show(new RelativePoint(selectedComponent.getDelegee().getParent(), pnt));
  }

  public interface StringDescriptorVisitor<T extends IComponent> {
    boolean visit(T component, StringDescriptor descriptor);
  }


  public static void iterateStringDescriptors(final IComponent component,
                                              final StringDescriptorVisitor<IComponent> visitor) {
    iterate(component, new ComponentVisitor<IComponent>() {

      public boolean visit(final IComponent component) {
        for (IProperty prop : component.getModifiedProperties()) {
          Object value = prop.getPropertyValue(component);
          if (value instanceof StringDescriptor) {
            if (!visitor.visit(component, (StringDescriptor)value)) {
              return false;
            }
          }
        }
        if (component.getParentContainer() instanceof ITabbedPane) {
          StringDescriptor tabTitle =
            ((ITabbedPane)component.getParentContainer()).getTabProperty(component, ITabbedPane.TAB_TITLE_PROPERTY);
          if (tabTitle != null && !visitor.visit(component, tabTitle)) {
            return false;
          }
          StringDescriptor tabToolTip =
            ((ITabbedPane)component.getParentContainer()).getTabProperty(component, ITabbedPane.TAB_TOOLTIP_PROPERTY);
          if (tabToolTip != null && !visitor.visit(component, tabToolTip)) {
            return false;
          }
        }
        if (component instanceof IContainer) {
          final StringDescriptor borderTitle = ((IContainer)component).getBorderTitle();
          if (borderTitle != null && !visitor.visit(component, borderTitle)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public static void clearSelection(@NotNull final RadContainer container) {
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
   *
   * @return the found component.
   */
  @Nullable
  public static IComponent findComponent(@NotNull final IComponent component, @NotNull final String id) {
    if (id.equals(component.getId())) {
      return component;
    }
    if (!(component instanceof IContainer)) {
      return null;
    }

    final IContainer uiContainer = (IContainer)component;
    for (int i = 0; i < uiContainer.getComponentCount(); i++) {
      final IComponent found = findComponent(uiContainer.getComponent(i), id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  @Nullable
  public static PsiClass findClassToBind(@NotNull final Module module, @NotNull final String classToBindName) {
    return JavaPsiFacade.getInstance(module.getProject())
      .findClass(classToBindName.replace('$', '.'), module.getModuleWithDependenciesScope());
  }

  public interface ComponentVisitor<Type extends IComponent> {
    /**
     * @return true if iteration should continue
     */
    boolean visit(Type component);
  }
}
