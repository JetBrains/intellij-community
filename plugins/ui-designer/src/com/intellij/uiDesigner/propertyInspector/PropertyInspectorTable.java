// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.ui.*;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.ErrorAnalyzer;
import com.intellij.uiDesigner.ErrorInfo;
import com.intellij.uiDesigner.Properties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.ShowJavadocAction;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.properties.*;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.IndentedIcon;
import com.intellij.util.ui.UIUtil;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.plaf.TableUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;

public final class PropertyInspectorTable extends JBTable implements UiDataProvider {
  private static final Logger LOG = Logger.getInstance(PropertyInspectorTable.class);

  private static final int PROPERTY_INDENT = 11;

  public static final DataKey<PropertyInspectorTable> DATA_KEY = DataKey.create(PropertyInspectorTable.class.getName());

  private static final Color SYNTHETIC_PROPERTY_BACKGROUND = new JBColor(Gray._230, UIUtil.getPanelBackground().brighter());
  private static final Color SYNTHETIC_SUBPROPERTY_BACKGROUND = new JBColor(Gray._240, UIUtil.getPanelBackground().brighter());

  private final ComponentTree myComponentTree;
  private final ArrayList<Property> myProperties;
  private final MyModel myModel;
  private final MyCompositeTableCellRenderer myCellRenderer;
  private final MyCellEditor myCellEditor;
  private GuiEditor myEditor;
  /**
   * This listener gets notifications from current property editor
   */
  private final MyPropertyEditorListener myPropertyEditorListener;
  /**
   * Updates UIs of synthetic properties
   */
  private Disposable myLafManagerDisposable;
  private final MyLafManagerListener myLafManagerListener;
  /**
   * This is property exists in this map then it's expanded.
   * It means that its children is visible.
   */
  private final HashSet<String> myExpandedProperties;
  /**
   * Component to be edited
   */
  private final @NotNull List<RadComponent> mySelection = new ArrayList<>();
  /**
   * If true then inspector will show "expert" properties
   */
  private boolean myShowExpertProperties;

  private final Map<HighlightSeverity, SimpleTextAttributes> myHighlightAttributes = new HashMap<>();
  private final Map<HighlightSeverity, SimpleTextAttributes> myModifiedHighlightAttributes = new HashMap<>();

  private final ClassToBindProperty myClassToBindProperty;
  private final BindingProperty myBindingProperty;
  private final BorderProperty myBorderProperty;
  private final LayoutManagerProperty myLayoutManagerProperty = new LayoutManagerProperty();
  private final ButtonGroupProperty myButtonGroupProperty = new ButtonGroupProperty();

  private boolean myInsideSynch;
  private boolean myStoppingEditing;
  private final Project myProject;

  private static final @NonNls String ourHelpID = "guiDesigner.uiTour.inspector";

  PropertyInspectorTable(Project project, final @NotNull ComponentTree componentTree) {
    myProject = project;
    myClassToBindProperty = new ClassToBindProperty(project);
    myBindingProperty = new BindingProperty(project);
    myBorderProperty = new BorderProperty(project);

    myPropertyEditorListener = new MyPropertyEditorListener();
    myLafManagerListener = new MyLafManagerListener();
    myComponentTree=componentTree;
    myProperties = new ArrayList<>();
    myExpandedProperties = new HashSet<>();
    myModel = new MyModel();
    setModel(myModel);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myCellRenderer = new MyCompositeTableCellRenderer();
    myCellEditor = new MyCellEditor();

    TableHoverListener.DEFAULT.removeFrom(this);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(final MouseEvent e){
        final int row = rowAtPoint(e.getPoint());
        final int column = columnAtPoint(e.getPoint());
        if (row == -1 || column == -1){
          return;
        }
        final Property property = myProperties.get(row);
        int indent = getPropertyIndentDepth(property) * getScaledPropertyIndent();
        final Rectangle rect = getCellRect(row, convertColumnIndexToView(0), false);

        Component rendererComponent = myCellRenderer.getTableCellRendererComponent(PropertyInspectorTable.this,
                                                                                   property, false, false, row, column);
        if (!rect.contains(e.getX(), e.getY()) ||
            !(rendererComponent instanceof ColoredTableCellRenderer) ||
            ((ColoredTableCellRenderer)rendererComponent).findFragmentAt(e.getX()) != SimpleColoredComponent.FRAGMENT_ICON ||
            e.getX() < rect.x + indent) {
          return;
        }

        final Property[] children = getPropChildren(property);
        if (children.length == 0) {
          return;
        }

        if (isPropertyExpanded(property)) {
          collapseProperty(row);
        }
        else {
          expandProperty(row);
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        int row = rowAtPoint(e.getPoint());
        int column = columnAtPoint(e.getPoint());
        if (row >= 0 && column == 0) {
          final Property property = myProperties.get(row);
          if (getPropChildren(property).length == 0) {
            startEditing(row);
            return true;
          }
        }
        return false;
      }
    }.installOn(this);


    final AnAction quickJavadocAction = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC);
    new ShowJavadocAction().registerCustomShortcutSet(
      quickJavadocAction.getShortcutSet(), this
    );

    // Popup menu
    PopupHandler.installPopupMenu(
      this,
      IdeActions.GROUP_GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP,
      ActionPlaces.GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP);
  }

  public void setEditor(final GuiEditor editor) {
    finishEditing();
    myEditor = editor;
    if (myEditor == null) {
      mySelection.clear();
      myProperties.clear();
      myModel.fireTableDataChanged();
    }
  }

  /**
   * @return currently selected {@link IntrospectedProperty} or {@code null}
   * if nothing selected or synthetic property is selected.
   */
  public @Nullable IntrospectedProperty getSelectedIntrospectedProperty(){
    Property property = getSelectedProperty();
    if (!(property instanceof IntrospectedProperty)) {
      return null;
    }

    return (IntrospectedProperty)property;
  }

  public @Nullable Property getSelectedProperty() {
    final int selectedRow = getSelectedRow();
    if(selectedRow < 0 || selectedRow >= getRowCount()){
      return null;
    }

    return myProperties.get(selectedRow);
  }

  public @Nullable String getSelectedRadComponentClassName() {
    if (mySelection.isEmpty()) return null;
    String className = mySelection.get(0).getComponentClassName();
    for (int i = 1; i < mySelection.size(); i++) {
      if (!Objects.equals(mySelection.get(i).getComponentClassName(), className)) {
        return null;
      }
    }
    return className;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    GuiEditor designer = myProject.isDisposed() ? null : DesignerToolWindowManager.getInstance(myProject).getActiveFormEditor();
    sink.set(DATA_KEY, this);
    sink.set(GuiEditor.DATA_KEY, myEditor);
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, designer == null ? null : designer.getEditor());
    sink.set(PlatformCoreDataKeys.HELP_ID, ourHelpID);
    IntrospectedProperty<?> introspectedProperty = getSelectedIntrospectedProperty();
    String radComponentClassName = getSelectedRadComponentClassName();
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      if (introspectedProperty == null || radComponentClassName == null || myEditor == null) {
        return null;
      }
      PsiClass aClass = JavaPsiFacade.getInstance(myEditor.getProject()).findClass(
        radComponentClassName, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myEditor.getModule()));
      if (aClass == null) {
        return null;
      }
      PsiMethod getter = PropertyUtilBase.findPropertyGetter(aClass, introspectedProperty.getName(), false, true);
      if (getter != null) {
        return getter;
      }
      return PropertyUtilBase.findPropertySetter(aClass, introspectedProperty.getName(), false, true);
    });
    sink.lazy(CommonDataKeys.PSI_FILE, () -> {
      return myEditor != null ? PsiManager.getInstance(myEditor.getProject()).findFile(myEditor.getFile()) : null;
    });
  }

  /**
   * Sets whenther "expert" properties are shown or not
   */
  void setShowExpertProperties(final boolean showExpertProperties){
    if(myShowExpertProperties == showExpertProperties){
      return;
    }
    myShowExpertProperties = showExpertProperties;
    if (myEditor != null) {
      synchWithTree(true);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();

    if (myLafManagerDisposable != null) {
      Disposer.dispose(myLafManagerDisposable);
    }
    myLafManagerDisposable = Disposer.newDisposable();
    ApplicationManager.getApplication().getMessageBus().connect(myLafManagerDisposable).subscribe(LafManagerListener.TOPIC, myLafManagerListener);
  }

  @Override
  public void removeNotify() {
    if (myLafManagerDisposable != null) {
      Disposer.dispose(myLafManagerDisposable);
      myLafManagerDisposable = null;
    }

    super.removeNotify();
  }

  /**
   * Standard JTable's UI has non convenient keybinding for
   * editing. Therefore we have to replace some standard actions.
   */
  @Override
  public void setUI(final TableUI ui){
    super.setUI(ui);

    // Customize action and input maps
    final @NonNls ActionMap actionMap=getActionMap();
    final @NonNls InputMap focusedInputMap=getInputMap(JComponent.WHEN_FOCUSED);
    final @NonNls InputMap ancestorInputMap=getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    actionMap.put(TableActions.Up.ID, new MySelectPreviousRowAction());

    actionMap.put(TableActions.Down.ID, new MySelectNextRowAction());

    actionMap.put("startEditing",new MyStartEditingAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2,0),"startEditing");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_F2,0));

    actionMap.put("smartEnter",new MyEnterAction());
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"smartEnter");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0));

    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel");
    ancestorInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"cancel");

    actionMap.put("expandCurrent", new MyExpandCurrentAction(true));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,0),"expandCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ADD,0));

    actionMap.put("collapseCurrent", new MyExpandCurrentAction(false));
    focusedInputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT,0),"collapseCurrent");
    ancestorInputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT,0));
  }

  @Override
  public void setValueAt(final Object aValue, final int row, final int column) {
    final Property property = myProperties.get(row);
    super.setValueAt(aValue, row, column);
    // We need to repaint whole inspector because change of one property
    // might causes change of another property.
    if (property.needRefreshPropertyList()) {
      synchWithTree(true);
    }
    repaint();
  }

  /**
   * Gets first selected component from ComponentTree and sets it for editing.
   * The method tries to keep selection in the list, so if new component has the property
   * which is already selected then the new value will be
   * also selected. It is very convenient.
   *
   * @param forceSynch if {@code false} and selected component in the ComponentTree
   * is the same as current component in the PropertyInspector then method does
   * nothing such sace. If {@code true} then inspector is forced to resynch.
   */
  public void synchWithTree(final boolean forceSynch){
    if (myInsideSynch) {
      return;
    }
    myInsideSynch = true;
    try {
      RadComponent[] newSelection = myComponentTree.getSelectedComponents();
      if (!forceSynch && mySelection.size() == newSelection.length) {
        boolean anyChanges = false;
        for(RadComponent c: newSelection) {
          if (!mySelection.contains(c)) {
            anyChanges = true;
            break;
          }
        }
        if (!anyChanges) return;
      }

      mySelection.clear();
      Collections.addAll(mySelection, newSelection);

      if (isEditing()){
        cellEditor.stopCellEditing();
      }

      // Store selected property
      final int selectedRow=getSelectedRow();
      Property selectedProperty=null;
      if(selectedRow >= 0 && selectedRow < myProperties.size()){
        selectedProperty=myProperties.get(selectedRow);
      }

      collectPropertiesForSelection();
      myModel.fireTableDataChanged();

      // Try to restore selection
      final ArrayList<Property> reversePath= new ArrayList<>(2);
      while(selectedProperty!=null){
        reversePath.add(selectedProperty);
        selectedProperty=selectedProperty.getParent();
      }
      int indexToSelect=-1;
      for(int i=reversePath.size()-1;i>=0;i--){
        final Property property=reversePath.get(i);
        int index=findPropertyByName(myProperties, property.getName());
        if(index==-1 && indexToSelect!=-1){ // try to expand parent and try again
          expandProperty(indexToSelect);
          index=findPropertyByName(myProperties, property.getName());
          if(index!=-1){
            indexToSelect=index;
          }else{
            break;
          }
        }else{
          indexToSelect=index;
        }
      }

      if(indexToSelect!=-1){
        getSelectionModel().setSelectionInterval(indexToSelect,indexToSelect);
      }else if(getRowCount()>0){
        // Select first row if it's impossible to restore selection
        getSelectionModel().setSelectionInterval(0,0);
      }
      TableUtil.scrollSelectionToVisible(this);
    }
    finally {
      myInsideSynch = false;
    }
  }

  private void collectPropertiesForSelection() {
    myProperties.clear();
    if (!mySelection.isEmpty()) {
      collectProperties(mySelection.get(0), myProperties);

      for(int propIndex=myProperties.size()-1; propIndex >= 0; propIndex--) {
        if (!myProperties.get(propIndex).appliesToSelection(mySelection)) {
          myProperties.remove(propIndex);
        }
      }

      for(int i=1; i<mySelection.size(); i++) {
        ArrayList<Property> otherProperties = new ArrayList<>();
        collectProperties(mySelection.get(i), otherProperties);
        for(int propIndex=myProperties.size()-1; propIndex >= 0; propIndex--) {
          final Property prop = myProperties.get(propIndex);
          int otherPropIndex = findPropertyByName(otherProperties, prop.getName());
          if (otherPropIndex < 0) {
            myProperties.remove(propIndex);
            continue;
          }
          final Property otherProp = otherProperties.get(otherPropIndex);
          if (!otherProp.getClass().equals(prop.getClass())) {
            myProperties.remove(propIndex);
            continue;
          }
          Property[] children = prop.getChildren(mySelection.get(0));
          Property[] otherChildren = otherProp.getChildren(mySelection.get(i));
          if (children.length != otherChildren.length) {
            myProperties.remove(propIndex);
            continue;
          }
          for(int childIndex=0; childIndex<children.length; childIndex++) {
            if (!Objects.equals(children[childIndex].getName(), otherChildren[childIndex].getName())) {
              myProperties.remove(propIndex);
              break;
            }
          }
        }
      }
    }
  }

  /**
   * @return index of the property with specified {@code name}.
   * If there is no such property then the method returns {@code -1}.
   */
  private static int findPropertyByName(final ArrayList<Property> properties, final String name){
    for(int i=properties.size()-1;i>=0;i--){
      final Property property=properties.get(i);
      if(property.getName().equals(name)){
        return i;
      }
    }
    return -1;
  }

  /**
   * Populates result list with the properties available for the specified
   * component
   */
  private void collectProperties(final RadComponent component, final ArrayList<Property> result) {
    if (component instanceof RadRootContainer){
      addProperty(result, myClassToBindProperty);
    }
    else {
      if (!(component instanceof RadVSpacer || component instanceof RadHSpacer)){
        addProperty(result, myBindingProperty);
        addProperty(result, CustomCreateProperty.getInstance(myProject));
      }

      if(component instanceof RadContainer container){
        if (container.getLayoutManager().getName() != null) {
          addProperty(result, myLayoutManagerProperty);
        }
        addProperty(result, myBorderProperty);

        final Property[] containerProperties = container.getLayoutManager().getContainerProperties(myProject);
        addApplicableProperties(containerProperties, container, result);
      }

      final RadContainer parent = component.getParent();
      if (parent != null) {
        final Property[] properties = parent.getLayoutManager().getComponentProperties(myProject, component);
        addApplicableProperties(properties, component, result);
      }

      if (component.getDelegee() instanceof AbstractButton &&
          !(component.getDelegee() instanceof JButton)) {
        addProperty(result, myButtonGroupProperty);
      }
      if (!(component instanceof RadVSpacer || component instanceof RadHSpacer)) {
        addProperty(result, ClientPropertiesProperty.getInstance(myProject));
      }

      if (component.hasIntrospectedProperties()) {
        final Class componentClass = component.getComponentClass();
        final IntrospectedProperty[] introspectedProperties =
          Palette.getInstance(myEditor.getProject()).getIntrospectedProperties(component);
        final Properties properties = Properties.getInstance();
        for (final IntrospectedProperty property: introspectedProperties) {
          if (!property.appliesTo(component)) continue;
          if (!myShowExpertProperties && properties.isExpertProperty(component.getModule(), componentClass, property.getName()) &&
            !isModifiedForSelection(property)) {
            continue;
          }
          addProperty(result, property);
        }
      }
    }
  }

  private void addApplicableProperties(final Property[] containerProperties,
                                       final RadComponent component,
                                       final ArrayList<Property> result) {
    for(Property prop: containerProperties) {
      //noinspection unchecked
      if (prop.appliesTo(component)) {
        addProperty(result, prop);
      }
    }
  }

  private void addProperty(final ArrayList<Property> result, final Property property) {
    result.add(property);
    if (isPropertyExpanded(property)) {
      for(Property child: getPropChildren(property)) {
        addProperty(result, child);
      }
    }
  }

  private boolean isPropertyExpanded(final Property property) {
    return myExpandedProperties.contains(getDottedName(property));
  }

  private static String getDottedName(final Property property) {
    final Property parent = property.getParent();
    if (parent != null) {
      return parent.getName() + "." + property.getName();
    }
    return property.getName();
  }

  private static int getPropertyIndentDepth(final Property property) {
    final Property parent = property.getParent();
    if (parent != null) {
      return parent.getParent() != null ? 2 : 1;
    }
    return 0;
  }

  private static int getScaledPropertyIndent() {
    return JBUIScale.scale(PROPERTY_INDENT);
  }

  private Property[] getPropChildren(final Property property) {
    return property.getChildren(mySelection.get(0));
  }

  @Override
  public TableCellEditor getCellEditor(final int row, final int column){
    final PropertyEditor editor = myProperties.get(row).getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener); // we do not need to add listener on every invocation
    editor.addPropertyEditorListener(myPropertyEditorListener);
    myCellEditor.setEditor(editor);
    return myCellEditor;
  }

  @Override
  public TableCellRenderer getCellRenderer(final int row, final int column){
    return myCellRenderer;
  }

  /*
   * This method is overriden due to bug in the JTree. The problem is that
   * JTree does not properly repaint edited cell if the editor is opaque or
   * has opaque child components.
   */
  @Override
  public boolean editCellAt(final int row, final int column, final EventObject e){
    final boolean result = super.editCellAt(row, column, e);
    final Rectangle cellRect = getCellRect(row, column, true);
    repaint(cellRect);
    return result;
  }

  /**
   * Starts editing property with the specified {@code index}.
   * The method does nothing is property isn't editable.
   */
  private void startEditing(final int index){
    final Property property=myProperties.get(index);
    final PropertyEditor editor=property.getEditor();
    if(editor==null){
      return;
    }
    editCellAt(index,convertColumnIndexToView(1));
    LOG.assertTrue(editorComp!=null);
    // Now we have to request focus into the editor component
    JComponent prefComponent = editor.getPreferredFocusedComponent((JComponent)editorComp);
    if(prefComponent == null){ // use default policy to find preferred focused component
      prefComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent((JComponent)editorComp);
    }
    if (prefComponent != null) {
      prefComponent.requestFocusInWindow();
    }
  }

  private void finishEditing(){
    if(editingRow==-1){
      return;
    }
    editingStopped(new ChangeEvent(cellEditor));
  }

  @Override
  public void editingStopped(final ChangeEvent ignored){
    LOG.assertTrue(isEditing());
    LOG.assertTrue(editingRow!=-1);
    if (myStoppingEditing) {
      return;
    }
    myStoppingEditing = true;
    final Property property=myProperties.get(editingRow);
    final PropertyEditor editor=property.getEditor();
    editor.removePropertyEditorListener(myPropertyEditorListener);
    try {
      if (myEditor != null && !myEditor.isUndoRedoInProgress()) {
        final Object value = editor.getValue();
        setValueAt(value, editingRow, editingColumn);
      }
    }
    catch (final Exception exc) {
      showInvalidInput(exc);
    }
    finally {
      removeEditor();
      myStoppingEditing = false;
    }
  }

  private static void showInvalidInput(final Exception exc) {
    final Throwable cause = exc.getCause();
    String message;
    if(cause != null){
      message = cause.getMessage();
    }
    else{
      message = exc.getMessage();
    }
    if (message == null || message.isEmpty()) {
      message = UIDesignerBundle.message("error.no.message");
    }
    Messages.showMessageDialog(UIDesignerBundle.message("error.setting.value", message),
                               UIDesignerBundle.message("title.invalid.input"), Messages.getErrorIcon());
  }

  /**
   * Expands property with the specified index. The method fires event that
   * model changes and keeps currently selected row.
   */
  private void expandProperty(final int index){
    final int selectedRow=getSelectedRow();

    // Expand property
    final Property property=myProperties.get(index);
    final String dottedName = getDottedName(property);

    // it's possible that property was expanded and we switched to a component which doesn't have this property
    if (myExpandedProperties.contains(dottedName)) return;
    myExpandedProperties.add(dottedName);

    final Property[] children=getPropChildren(property);
    for (int i = 0; i < children.length; i++) {
      myProperties.add(index + i + 1, children[i]);
    }
    myModel.fireTableDataChanged();

    // Restore selected row
    if(selectedRow!=-1){
      getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
    }
  }

  /**
   * Collapse property with the specified index. The method fires event that
   * model changes and keeps currently selected row.
   */
  private void collapseProperty(final int index){
    final int selectedRow=getSelectedRow();

    // Expand property
    final Property property=myProperties.get(index);
    LOG.assertTrue(isPropertyExpanded(property));
    myExpandedProperties.remove(getDottedName(property));

    final Property[] children=getPropChildren(property);
    for (int i=0; i<children.length; i++){
      myProperties.remove(index + 1);
    }
    myModel.fireTableDataChanged();

    // Restore selected row
    if(selectedRow!=-1){
      getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
    }
  }

  @Nullable
  ErrorInfo getErrorInfoForRow(final int row) {
    LOG.assertTrue(row < myProperties.size());
    if (mySelection.size() != 1) {
      return null;
    }
    RadComponent component = mySelection.get(0);
    final Property property = myProperties.get(row);
    ErrorInfo errorInfo = null;
    if(myClassToBindProperty.equals(property)){
      errorInfo = (ErrorInfo)component.getClientProperty(ErrorAnalyzer.CLIENT_PROP_CLASS_TO_BIND_ERROR);
    }
    else if(myBindingProperty.equals(property)){
      errorInfo = (ErrorInfo)component.getClientProperty(ErrorAnalyzer.CLIENT_PROP_BINDING_ERROR);
    }
    else {
      //noinspection unchecked
      ArrayList<ErrorInfo> errors = (ArrayList<ErrorInfo>) component.getClientProperty(ErrorAnalyzer.CLIENT_PROP_ERROR_ARRAY);
      if (errors != null) {
        for(ErrorInfo err: errors) {
          if (property.getName().equals(err.getPropertyName())) {
            errorInfo = err;
            break;
          }
        }
      }
    }
    return errorInfo;
  }

  /**
   * @return first error for the property at the specified row. If component doesn't contain
   * any error then the method returns {@code null}.
   */
  private @Nullable String getErrorForRow(final int row){
    LOG.assertTrue(row < myProperties.size());
    final ErrorInfo errorInfo = getErrorInfoForRow(row);
    return errorInfo != null ? errorInfo.myDescription : null;
  }

  @Override
  public String getToolTipText(final MouseEvent e) {
    final int row = rowAtPoint(e.getPoint());
    if(row == -1){
      return null;
    }
    return getErrorForRow(row);
  }

  private Object getSelectionValue(final Property property) {
    if (mySelection.isEmpty()) {
      return null;
    }
    //noinspection unchecked
    Object result = property.getValue(mySelection.get(0));
    for(int i=1; i<mySelection.size(); i++) {
      Object otherValue = null;
      if (property instanceof IntrospectedProperty) {
        IntrospectedProperty[] props = Palette.getInstance(myProject).getIntrospectedProperties(mySelection.get(i));
        for(IntrospectedProperty otherProperty: props) {
          if (otherProperty.getName().equals(property.getName())) {
            otherValue = otherProperty.getValue(mySelection.get(i));
            break;
          }
        }
      }
      else {
        //noinspection unchecked
        otherValue = property.getValue(mySelection.get(i));
      }
      if (!Comparing.equal(result, otherValue)) {
        return null;
      }
    }
    return result;
  }

  /**
   * @return false if some of the set value operations have failed; true if everything successful
   */
  private boolean setSelectionValue(Property property, Object newValue) {
    if (!setPropValue(property, mySelection.get(0), newValue)) return false;
    for(int i=1; i<mySelection.size(); i++) {
      if (property instanceof IntrospectedProperty) {
        IntrospectedProperty[] props = Palette.getInstance(myProject).getIntrospectedProperties(mySelection.get(i));
        for(IntrospectedProperty otherProperty: props) {
          if (otherProperty.getName().equals(property.getName())) {
            if (!setPropValue(otherProperty, mySelection.get(i), newValue)) return false;
            break;
          }
        }
      }
      else {
        if (!setPropValue(property, mySelection.get(i), newValue)) return false;
      }
    }
    return true;
  }

  private static boolean setPropValue(final Property property, final RadComponent c, final Object newValue) {
    try {
      //noinspection unchecked
      property.setValue(c, newValue);
    }
    catch (Throwable e) {
      LOG.debug(e);
      String message = ExceptionUtilRt.unwrapException(e, InvocationTargetException.class).getMessage();
      Messages.showMessageDialog(message, UIDesignerBundle.message("title.invalid.input"), Messages.getErrorIcon());
      return false;
    }
    return true;
  }

  public boolean isModifiedForSelection(final Property property) {
    for(RadComponent c: mySelection) {
      //noinspection unchecked
      if (property.isModified(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adapter to TableModel
   */
  private final class MyModel extends AbstractTableModel {
    private final String[] myColumnNames;

    MyModel(){
      myColumnNames=new String[]{
        UIDesignerBundle.message("column.property"),
        UIDesignerBundle.message("column.value")};
    }

    @Override
    public int getColumnCount(){
      return 2;
    }

    @Override
    public String getColumnName(final int column){
      return myColumnNames[column];
    }

    @Override
    public int getRowCount(){
      return myProperties.size();
    }

    @Override
    public boolean isCellEditable(final int row, final int column){
      return  column==1 && myProperties.get(row).getEditor() != null;
    }

    @Override
    public Object getValueAt(final int row, final int column){
      return myProperties.get(row);
    }

    @Override
    public void setValueAt(final Object newValue, final int row, final int column){
      if (column != 1){
        throw new IllegalArgumentException("wrong index: " + column);
      }
      setValueAtRow(row, newValue);
    }

    boolean setValueAtRow(final int row, final Object newValue) {
      final Property property=myProperties.get(row);

      // Optimization: do nothing if value doesn't change
      final Object oldValue=getSelectionValue(property);
      boolean retVal = true;
      if(!Comparing.equal(oldValue,newValue)){
        final GuiEditor editor = myEditor;
        if (!editor.ensureEditable()) {
          return false;
        }
        final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          result.set(setSelectionValue(property, newValue));

          editor.refreshAndSave(false);
        }, UIDesignerBundle.message("command.set.property.value"), null);

        retVal = result.get().booleanValue();
      }
      if (property.needRefreshPropertyList() && retVal) {
        synchWithTree(true);
      }
      return retVal;
    }
  }

  private final class MyPropertyEditorListener implements PropertyEditorListener {
    @Override
    public void valueCommitted(final @NotNull PropertyEditor source, final boolean continueEditing, final boolean closeEditorOnError){
      if(isEditing()){
        final Object value;
        final TableCellEditor tableCellEditor = cellEditor;
        try {
          value = tableCellEditor.getCellEditorValue();
        }
        catch (final Exception exc) {
          showInvalidInput(exc);
          return;
        }
        boolean valueAccepted = myModel.setValueAtRow(editingRow, value);
        if (valueAccepted) {
          if (!continueEditing) tableCellEditor.stopCellEditing();
        }
        else {
          if (closeEditorOnError) tableCellEditor.cancelCellEditing();
        }
      }
    }

    @Override
    public void editingCanceled(final @NotNull PropertyEditor source) {
      if(isEditing()){
        cellEditor.cancelCellEditing();
      }
    }
  }

  private final class MyCompositeTableCellRenderer implements TableCellRenderer{
    /**
     * This renderer paints first column with property names
     */
    private final ColoredTableCellRenderer myPropertyNameRenderer;
    private final ColoredTableCellRenderer myErrorRenderer;
    private final Icon myExpandIcon;
    private final Icon myCollapseIcon;
    private final Icon myIndentedExpandIcon;
    private final Icon myIndentedCollapseIcon;
    private final Icon[] myIndentIcons = new Icon[3];

    MyCompositeTableCellRenderer(){
      myPropertyNameRenderer = new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(
          final @NotNull JTable table,
          final Object value,
          final boolean selected,
          final boolean hasFocus,
          final int row,
          final int column
        ) {
          // We will append text later in the
          setPaintFocusBorder(false);
          setFocusBorderAroundIcon(true);
        }
      };

      myErrorRenderer = new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          setPaintFocusBorder(false);
        }
      };

      myExpandIcon = UIDesignerIcons.ExpandNode;
      myCollapseIcon = UIDesignerIcons.CollapseNode;
      for (int i = 0; i < myIndentIcons.length; i++) {
        myIndentIcons[i] = EmptyIcon.create(myExpandIcon.getIconWidth() + getScaledPropertyIndent() * i, myExpandIcon.getIconHeight());
      }
      myIndentedExpandIcon = new IndentedIcon(myExpandIcon, PROPERTY_INDENT);
      myIndentedCollapseIcon = new IndentedIcon(myCollapseIcon, PROPERTY_INDENT);
    }

    @Override
    public Component getTableCellRendererComponent(
      final JTable table,
      final @NotNull Object value,
      final boolean selected,
      final boolean hasFocus,
      final int row,
      int column
    ){
      myPropertyNameRenderer.getTableCellRendererComponent(table,value,selected,hasFocus,row,column);

      column=table.convertColumnIndexToModel(column);
      final Property property=(Property)value;

      final Color background;
      final Property parent = property.getParent();
      if (property instanceof IntrospectedProperty){
        background = table.getBackground();
      }
      else {
        background = parent == null ? SYNTHETIC_PROPERTY_BACKGROUND : SYNTHETIC_SUBPROPERTY_BACKGROUND;
      }

      if (!selected){
        myPropertyNameRenderer.setBackground(background);
      }

      if(column==0){ // painter for first column
        SimpleTextAttributes attrs = getTextAttributes(row, property);
        myPropertyNameRenderer.append(property.getName(), attrs);

        // 2. Icon
        if(getPropChildren(property).length>0) {
          // This is composite property and we have to show +/- sign
          if (parent != null) {
            if(isPropertyExpanded(property)){
              myPropertyNameRenderer.setIcon(myIndentedCollapseIcon);
            }else{
              myPropertyNameRenderer.setIcon(myIndentedExpandIcon);
            }
          }
          else {
            if(isPropertyExpanded(property)){
              myPropertyNameRenderer.setIcon(myCollapseIcon);
            }else{
              myPropertyNameRenderer.setIcon(myExpandIcon);
            }
          }
        }else{
          // If property doesn't have children then we have shift its text
          // to the right
          myPropertyNameRenderer.setIcon(myIndentIcons [getPropertyIndentDepth(property)]);
        }
      }
      else if(column==1){ // painter for second column
        try {
          final PropertyRenderer renderer=property.getRenderer();
          //noinspection unchecked
          final JComponent component = renderer.getComponent(myEditor.getRootContainer(), getSelectionValue(property),
                                                             selected, hasFocus);
          if (!selected) {
            component.setBackground(background);
          }
          if (isModifiedForSelection(property)) {
            component.setFont(table.getFont().deriveFont(Font.BOLD));
          }
          else {
            component.setFont(table.getFont());
          }

          return component;
        }
        catch(Exception ex) {
          LOG.debug(ex);
          myErrorRenderer.clear();
          myErrorRenderer.append(UIDesignerBundle.message("error.getting.value", ex.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
          return myErrorRenderer;
        }
      }
      else{
        throw new IllegalArgumentException("wrong column: "+column);
      }

      if (!selected) {
        myPropertyNameRenderer.setForeground(PropertyInspectorTable.this.getForeground());
        if(property instanceof IntrospectedProperty){
          final RadComponent component = mySelection.get(0);
          final Class componentClass = component.getComponentClass();
          if (Properties.getInstance().isExpertProperty(component.getModule(), componentClass, property.getName())) {
            myPropertyNameRenderer.setForeground(Color.LIGHT_GRAY);
          }
        }
      }

      return myPropertyNameRenderer;
    }

    private SimpleTextAttributes getTextAttributes(final int row, final Property property) {
      // 1. Text
      ErrorInfo errInfo = getErrorInfoForRow(row);

      SimpleTextAttributes result;
      boolean modified;
      try {
        modified = isModifiedForSelection(property);
      }
      catch(Exception ex) {
        // ignore exceptions here - they'll be reported as red property values
        modified = false;
      }
      if (errInfo == null) {
        result = modified ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
      }
      else {
        final HighlightSeverity severity = errInfo.getHighlightDisplayLevel().getSeverity();
        Map<HighlightSeverity, SimpleTextAttributes> cache = modified ? myModifiedHighlightAttributes : myHighlightAttributes;
        result = cache.get(severity);
        if (result == null) {
          final TextAttributesKey attrKey = SeverityRegistrar.getSeverityRegistrar(myProject).getHighlightInfoTypeBySeverity(severity).getAttributesKey();
          TextAttributes textAttrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attrKey);
          if (modified) {
            textAttrs = textAttrs.clone();
            textAttrs.setFontType(textAttrs.getFontType() | Font.BOLD);
          }
          result = SimpleTextAttributes.fromTextAttributes(textAttrs);
          cache.put(severity, result);
        }
      }

      if (property instanceof IntrospectedProperty) {
        final RadComponent c = mySelection.get(0);
        if (Properties.getInstance().isPropertyDeprecated(c.getModule(), c.getComponentClass(), property.getName())) {
          return new SimpleTextAttributes(result.getBgColor(), result.getFgColor(), result.getWaveColor(),
                                          result.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT);
        }
      }

      return result;
    }
  }

  /**
   * This is adapter from PropertyEditor to TableCellEditor interface
   */
  private final class MyCellEditor extends AbstractCellEditor implements TableCellEditor{
    private PropertyEditor myEditor;

    public void setEditor(final @NotNull PropertyEditor editor){
      myEditor = editor;
    }

    @Override
    public Object getCellEditorValue(){
      try {
        return myEditor.getValue();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final @NotNull Object value, final boolean isSelected, final int row, final int column){
      final Property property=(Property)value;
      try {
        //noinspection unchecked
        final JComponent c = myEditor.getComponent(mySelection.get(0), getSelectionValue(property), null);
        if (c instanceof JComboBox) {
          c.putClientProperty(ComboBox.IS_TABLE_CELL_EDITOR_PROPERTY, Boolean.TRUE);
        }

        return c;
      }
      catch(Exception ex) {
        LOG.debug(ex);
        SimpleColoredComponent errComponent = new SimpleColoredComponent();
        errComponent.append(UIDesignerBundle.message("error.getting.value", ex.getMessage()), SimpleTextAttributes.ERROR_ATTRIBUTES);
        return errComponent;
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's SelectPreviousRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private final class MySelectPreviousRowAction extends AbstractAction{
    @Override
    public void actionPerformed(final ActionEvent e){
      final int rowCount=getRowCount();
      LOG.assertTrue(rowCount>0);
      int selectedRow=getSelectedRow();
      if(selectedRow!=-1){
        selectedRow -= 1;
      }
      selectedRow=(selectedRow+rowCount)%rowCount;
      if(isEditing()){
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
        startEditing(selectedRow);
      } else {
        getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's SelectNextRowAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private final class MySelectNextRowAction extends AbstractAction{
    @Override
    public void actionPerformed(final ActionEvent e){
      final int rowCount=getRowCount();
      LOG.assertTrue(rowCount>0);
      final int selectedRow=(getSelectedRow()+1)%rowCount;
      if(isEditing()){
        finishEditing();
        getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
        startEditing(selectedRow);
      }else{
        getSelectionModel().setSelectionInterval(selectedRow,selectedRow);
        scrollRectToVisible(getCellRect(selectedRow, 0, true));
      }
    }
  }

  /**
   * Reimplementation of LookAndFeel's StartEditingAction action.
   * Standard implementation isn't smart enough.
   *
   * @see javax.swing.plaf.basic.BasicTableUI
   */
  private final class MyStartEditingAction extends AbstractAction{
    @Override
    public void actionPerformed(final ActionEvent e){
      final int selectedRow=getSelectedRow();
      if(selectedRow==-1 || isEditing()){
        return;
      }

      startEditing(selectedRow);
    }
  }

  /**
   * Expands property which has children or start editing atomic
   * property.
   */
  private final class MyEnterAction extends AbstractAction{
    @Override
    public void actionPerformed(final ActionEvent e){
      final int selectedRow=getSelectedRow();
      if(isEditing() || selectedRow==-1){
        return;
      }

      final Property property=myProperties.get(selectedRow);
      if(getPropChildren(property).length>0){
        if(isPropertyExpanded(property)){
          collapseProperty(selectedRow);
        }else{
          expandProperty(selectedRow);
        }
      }else{
        startEditing(selectedRow);
      }
    }
  }

  private class MyExpandCurrentAction extends AbstractAction {
    private final boolean myExpand;

    MyExpandCurrentAction(final boolean expand) {
      myExpand = expand;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final int selectedRow=getSelectedRow();
      if(isEditing() || selectedRow==-1){
        return;
      }
      final Property property=myProperties.get(selectedRow);
      if(getPropChildren(property).length>0) {
        if (myExpand) {
          if (!isPropertyExpanded(property)) {
            expandProperty(selectedRow);
          }
        }
        else {
          if (isPropertyExpanded(property)) {
            collapseProperty(selectedRow);
          }
        }
      }
    }
  }

  /**
   * Updates UI of editors and renderers of all introspected properties
   */
  private final class MyLafManagerListener implements LafManagerListener{
    /**
     * Recursively updates renderer and editor UIs of all synthetic
     * properties.
     */
    private static void updateUI(final Property property){
      final PropertyRenderer renderer = property.getRenderer();
      renderer.updateUI();
      final PropertyEditor editor = property.getEditor();
      if(editor != null){
        editor.updateUI();
      }
      final Property[] children = property.getChildren(null);
      for (int i = children.length - 1; i >= 0; i--) {
        final Property child = children[i];
        if(!(child instanceof IntrospectedProperty)){
          updateUI(child);
        }
      }
    }

    @Override
    public void lookAndFeelChanged(final @NotNull LafManager source) {
      updateUI(myBorderProperty);
      updateUI(MarginProperty.getInstance(myProject));
      updateUI(HGapProperty.getInstance(myProject));
      updateUI(VGapProperty.getInstance(myProject));
      updateUI(HSizePolicyProperty.getInstance(myProject));
      updateUI(VSizePolicyProperty.getInstance(myProject));
      updateUI(HorzAlignProperty.getInstance(myProject));
      updateUI(VertAlignProperty.getInstance(myProject));
      updateUI(IndentProperty.getInstance(myProject));
      updateUI(UseParentLayoutProperty.getInstance(myProject));
      updateUI(MinimumSizeProperty.getInstance(myProject));
      updateUI(PreferredSizeProperty.getInstance(myProject));
      updateUI(MaximumSizeProperty.getInstance(myProject));
      updateUI(myButtonGroupProperty);
      updateUI(myLayoutManagerProperty);
      updateUI(SameSizeHorizontallyProperty.getInstance(myProject));
      updateUI(SameSizeVerticallyProperty.getInstance(myProject));
      updateUI(CustomCreateProperty.getInstance(myProject));
      updateUI(ClientPropertiesProperty.getInstance(myProject));
    }
  }


}