package com.intellij.uiDesigner.designSurface;

import com.intellij.ide.palette.impl.PaletteManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.LwNestedForm;
import com.intellij.uiDesigner.make.PsiNestedFormLoader;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InsertComponentProcessor extends EventProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.InsertComponentProcessor");

  private final GuiEditor myEditor;
  private boolean mySticky;
  private DropInfo myDropInfo;
  private RadComponent myInsertedComponent;
  private Point myInitialPoint;
  private Dimension myInitialSize;
  private boolean myShouldSetPreferredSizeIfNotResized;
  private GridInsertProcessor myGridInsertProcessor;

  public InsertComponentProcessor(@NotNull final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
  }

  public boolean isSticky() {
    return mySticky;
  }

  public void setSticky(final boolean sticky) {
    mySticky = sticky;
  }

  protected void processKeyEvent(final KeyEvent e) {}

  /**
   * TODO[vova] it would be fine to configure such "input" controls somewhere in palette
   * @return whether component is an input control or not
   */
  private static boolean isInputComponent(@NotNull final RadComponent component){
    final Class aClass = component.getComponentClass();
    if(
      AbstractButton.class.isAssignableFrom(aClass) ||
      JComboBox.class.isAssignableFrom(aClass) ||
      JList.class.isAssignableFrom(aClass) ||
      JSpinner.class.isAssignableFrom(aClass) ||
      JTabbedPane.class.isAssignableFrom(aClass) ||
      JTable.class.isAssignableFrom(aClass) ||
      JTextComponent.class.isAssignableFrom(aClass) ||
      JTree.class.isAssignableFrom(aClass)
    ){
      return true;
    }

    return false;
  }

  @NotNull
  private static String suggestBinding(final GuiEditor editor, @NotNull final String componentClassName){
    final int lastDotIndex = componentClassName.lastIndexOf('.');
    String shortClassName = componentClassName.substring(lastDotIndex + 1);

    // Here is euristic. Chop first 'J' letter for standard Swing classes.
    // Without 'J' bindings look better.
    //noinspection HardCodedStringLiteral
    if(
      shortClassName.length() > 1 && Character.isUpperCase(shortClassName.charAt(1)) &&
      componentClassName.startsWith("javax.swing.") &&
      StringUtil.startsWithChar(shortClassName, 'J')
    ){
      shortClassName = shortClassName.substring(1);
    }
    shortClassName = StringUtil.decapitalize(shortClassName);

    LOG.assertTrue(shortClassName.length() > 0);

    // Generate member name based on current code style
    //noinspection ForLoopThatDoesntUseLoopVariable
    for(int i = 0; true; i++){
      final String nameCandidate = shortClassName + (i + 1);
      final String binding = CodeStyleManager.getInstance(editor.getProject()).propertyNameToVariableName(
        nameCandidate,
        VariableKind.FIELD
      );

      if (!FormEditingUtil.bindingExists(editor.getRootContainer(), binding)) {
        return binding;
      }
    }
  }

  /**
   * Tries to create binding for {@link #myInsertedComponent}
   * @param editor
   * @param insertedComponent
   */
  public static void createBindingWhenDrop(final GuiEditor editor, final RadComponent insertedComponent) {
    if(isInputComponent(insertedComponent)){
      doCreateBindingWhenDrop(editor, insertedComponent);
    }
  }

  private static void doCreateBindingWhenDrop(final GuiEditor editor, final RadComponent insertedComponent) {
    // Now if the inserted component is a input control, we need to automatically create binding
    final String binding = suggestBinding(editor, insertedComponent.getComponentClassName());
    insertedComponent.setBinding(binding);

    // Try to create field in the corresponding bound class
    final String classToBind = editor.getRootContainer().getClassToBind();
    if(classToBind != null){
      final PsiClass aClass = FormEditingUtil.findClassToBind(editor.getModule(), classToBind);
      if(aClass != null){
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              CreateFieldFix.runImpl(editor.getProject(),
                                     editor.getRootContainer(),
                                     aClass,
                                     insertedComponent.getComponentClassName(),
                                     binding,
                                     false // silently skip all errors (if any)
              );
            }
          }
        );
      }
    }
  }

  protected void processMouseEvent(final MouseEvent e){
    final int id = e.getID();
   switch (id) {
        case  MouseEvent.MOUSE_PRESSED:
          processMousePressed(e);
          break;
        case  MouseEvent.MOUSE_RELEASED:
          processMouseReleased();
          break;
        case  MouseEvent.MOUSE_DRAGGED:
          processMouseDragged(e);
          break;
    }
  }

  private void processMouseDragged(final MouseEvent e) {
    if (myDropInfo != null && myDropInfo.myTargetContainer.isXY()) {
      final int width = e.getX() - myInitialPoint.x;
      final int height = e.getY() - myInitialPoint.y;

      final Dimension newSize = myInsertedComponent.getSize();

      if (width >= myInitialSize.width) {
        newSize.width = width;
      }
      if (height >= myInitialSize.height) {
        newSize.height = height;
      }
      myInsertedComponent.setSize(newSize);
      myEditor.refresh();
    }
  }

  private void processMouseReleased() {
    if (!mySticky) {
      PaletteManager.getInstance(myEditor.getProject()).clearActiveItem();
    }

    if (myDropInfo != null) {
      if (myDropInfo.myTargetContainer.isXY()) {
        Dimension newSize = myInsertedComponent.getSize();
        if (newSize.equals(myInitialSize) && myShouldSetPreferredSizeIfNotResized) {
          // if component dropped into XY and was not resized, make it preferred size
          newSize = myInsertedComponent.getPreferredSize();
        }
        Util.adjustSize(myInsertedComponent.getDelegee(), myInsertedComponent.getConstraints(), newSize);
        myInsertedComponent.setSize(newSize);
      }

      myEditor.refreshAndSave(true);
    }
  }

  private void processMousePressed(final MouseEvent e) {
    final ComponentItem item = PaletteManager.getInstance(myEditor.getProject()).getActiveItem(ComponentItem.class);
    processComponentInsert(e.getPoint(), item);
  }

  public void processComponentInsert(final Point point, final ComponentItem item) {
    myEditor.getActiveDecorationLayer().removeFeedback();
    myEditor.setDesignTimeInsets(2);

    if (!validateNestedFormInsert(item)) {
      return;
    }

    myInsertedComponent = createInsertedComponent(myEditor, item);

    final GridInsertLocation location = GridInsertProcessor.getGridInsertLocation(myEditor, point.x, point.y, 0);
    if (FormEditingUtil.canDrop(myEditor, point.x, point.y, 1) || location.getMode() != GridInsertMode.None) {
      CommandProcessor.getInstance().executeCommand(
        myEditor.getProject(),
        new Runnable(){
          public void run(){
            createBindingWhenDrop(myEditor, myInsertedComponent);

            final RadComponent[] components = new RadComponent[]{myInsertedComponent};
            if (location.getMode() == GridInsertMode.None) {
              myDropInfo = FormEditingUtil.drop(myEditor, point.x, point.y, components, new int[]{0}, new int[]{0});
            }
            else {
              myDropInfo = myGridInsertProcessor.processGridInsertOnDrop(location, components, null);
              if (myDropInfo == null) {
                return;
              }
            }

            FormEditingUtil.clearSelection(myEditor.getRootContainer());
            myInsertedComponent.setSelected(true);

            myInitialSize = null;
            myShouldSetPreferredSizeIfNotResized = true;
            if (myDropInfo.myTargetContainer.isXY()) {
              setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
              myInitialSize = myInsertedComponent.getSize();
              if (myInitialSize.width > 0 && myInitialSize.height > 0) {
                myShouldSetPreferredSizeIfNotResized = false;
              }
              else {
                // size was not specified as initial value
                myInitialSize = new Dimension(7, 7);
              }
              Util.adjustSize(myInsertedComponent.getDelegee(), myInsertedComponent.getConstraints(), myInitialSize);
              myInsertedComponent.setSize(myInitialSize);
            }

            if (!GuiDesignerConfiguration.getInstance(myEditor.getProject()).IRIDA_LAYOUT_MODE &&
                myInsertedComponent.getParent() instanceof RadRootContainer &&
                myInsertedComponent instanceof RadAtomicComponent) {
              GridBuildUtil.convertToGrid(myEditor);
              FormEditingUtil.clearSelection(myEditor.getRootContainer());
              myInsertedComponent.setSelected(true);
            }

            checkBindTopLevelPanel();

            myEditor.refreshAndSave(false);

            myInitialPoint = point;
          }

        },
        null,
        null
      );
    }
  }

  private boolean validateNestedFormInsert(final ComponentItem item) {
    final PsiManager manager = PsiManager.getInstance(myEditor.getProject());
    PsiFile[] boundForms = manager.getSearchHelper().findFormsBoundToClass(item.getClassName());
    if (boundForms.length > 0) {
      Set<String> usedFormNames = new HashSet<String>();
      PsiFile editedFormFile = manager.findFile(myEditor.getFile());
      usedFormNames.add(GuiEditorUtil.buildResourceName(editedFormFile));
      if (!validateNestedFormLoop(usedFormNames, GuiEditorUtil.buildResourceName(boundForms [0]))) {
        return false;
      }
    }
    return true;
  }

  private boolean validateNestedFormLoop(final Set<String> usedFormNames, final String formName) {
    if (usedFormNames.contains(formName)) {
      Messages.showErrorDialog(myEditor, "Adding this form is not allowed because it would create a loop of form nesting");
      return false;
    }
    final LwRootContainer rootContainer;
    try {
      rootContainer = new PsiNestedFormLoader(myEditor.getModule()).loadForm(formName);
    }
    catch (Exception e) {
      Messages.showErrorDialog(myEditor, "Error loading nested form: " + e.getMessage());
      return false;
    }
    final Set<String> thisFormNestedForms = new HashSet<String>();
    final Ref<Boolean> iterateResult = new Ref<Boolean>(Boolean.TRUE);
    FormEditingUtil.iterate(rootContainer, new FormEditingUtil.ComponentVisitor() {
      public boolean visit(final IComponent component) {
        if (component instanceof LwNestedForm) {
          LwNestedForm nestedForm = (LwNestedForm) component;
          if (!thisFormNestedForms.contains(nestedForm.getFormFileName())) {
            thisFormNestedForms.add(nestedForm.getFormFileName());
            if (!validateNestedFormLoop(usedFormNames, nestedForm.getFormFileName())) {
              iterateResult.set(Boolean.FALSE);
              return false;
            }
          }
        }
        return true;
      }
    });
    return iterateResult.get().booleanValue();
  }

  public static RadComponent createInsertedComponent(GuiEditor editor, ComponentItem item) {
    RadComponent result;
    final String id = editor.generateId();

    if (JScrollPane.class.getName().equals(item.getClassName())) {
      result = new RadScrollPane(editor.getModule(), id);
    }
    else if (item == Palette.getInstance(editor.getProject()).getPanelItem()) {
      result = new RadContainer(editor.getModule(), id);
    }
    else {
      if (VSpacer.class.getName().equals(item.getClassName())) {
        result = new RadVSpacer(editor.getModule(), id);
      }
      else if (HSpacer.class.getName().equals(item.getClassName())) {
        result = new RadHSpacer(editor.getModule(), id);
      }
      else if (JTabbedPane.class.getName().equals(item.getClassName())) {
        result = new RadTabbedPane(editor.getModule(), id);
      }
      else if (JSplitPane.class.getName().equals(item.getClassName())) {
        result = new RadSplitPane(editor.getModule(), id);
      }
      else {
        final PsiManager manager = PsiManager.getInstance(editor.getProject());
        PsiFile[] boundForms = manager.getSearchHelper().findFormsBoundToClass(item.getClassName());
        if (boundForms.length > 0) {
          try {
            result = new RadNestedForm(editor.getModule(), GuiEditorUtil.buildResourceName(boundForms [0]), id);
          }
          catch(Exception ex) {
            result = RadErrorComponent.create(
              editor.getModule(),
              id,
              item.getClassName(),
              null,
              ex.getMessage()
            );
          }
        }
        else {
          final ClassLoader loader = LoaderFactory.getInstance(editor.getProject()).getLoader(editor.getFile());
          try {
            final Class aClass = Class.forName(item.getClassName(), true, loader);
            result = new RadAtomicComponent(editor.getModule(), aClass, id);
          }
          catch (final Exception exc) {
            //noinspection NonConstantStringShouldBeStringBuffer
            String errorDescription = Utils.validateJComponentClass(loader, item.getClassName());
            if (errorDescription == null) {
              errorDescription = UIDesignerBundle.message("error.class.cannot.be.instantiated", item.getClassName());
              final String message = FormEditingUtil.getExceptionMessage(exc);
              if (message != null) {
                errorDescription += ": " + message;
              }
            }
            result = RadErrorComponent.create(
              editor.getModule(),
              id,
              item.getClassName(),
              null,
              errorDescription
            );
          }
        }
      }
    }
    result.init(item);
    return result;
  }

  private void checkBindTopLevelPanel() {
    if (myEditor.getRootContainer().getComponentCount() == 1) {
      final RadComponent component = myEditor.getRootContainer().getComponent(0);
      if (component.getBinding() == null) {
        if (component == myInsertedComponent ||
            (component instanceof RadContainer && ((RadContainer) component).getComponentCount() == 1 &&
             component == myInsertedComponent.getParent())) {
          doCreateBindingWhenDrop(myEditor, component);
        }
      }
    }
  }

  protected boolean cancelOperation() {
    myEditor.setDesignTimeInsets(2);
    myEditor.getActiveDecorationLayer().removeFeedback();
    return false;
  }

  public Cursor processMouseMoveEvent(final MouseEvent e) {
    return myGridInsertProcessor.processMouseMoveEvent(e.getX(), e.getY(), false, 1, 0);
  }
}
