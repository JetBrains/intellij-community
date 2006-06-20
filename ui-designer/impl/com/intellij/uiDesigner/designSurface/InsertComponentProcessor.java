package com.intellij.uiDesigner.designSurface;

import com.intellij.CommonBundle;
import com.intellij.ide.palette.impl.PaletteManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.core.Util;
import com.intellij.uiDesigner.make.PsiNestedFormLoader;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.ComponentItemDialog;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.uiDesigner.radComponents.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InsertComponentProcessor extends EventProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.designSurface.InsertComponentProcessor");

  private PaletteManager myPaletteManager;
  private final GuiEditor myEditor;
  private boolean mySticky;
  private RadComponent myInsertedComponent;
  private GridInsertProcessor myGridInsertProcessor;
  private ComponentItem myComponentToInsert;
  private DropLocation myLastLocation;

  private static Map<String, Class<? extends RadComponent>> myComponentClassMap = new HashMap<String, Class<? extends RadComponent>>();
  static {
    myComponentClassMap.put(JScrollPane.class.getName(), RadScrollPane.class);
    myComponentClassMap.put(JPanel.class.getName(), RadContainer.class);
    myComponentClassMap.put(VSpacer.class.getName(), RadVSpacer.class);
    myComponentClassMap.put(HSpacer.class.getName(), RadHSpacer.class);
    myComponentClassMap.put(JTabbedPane.class.getName(), RadTabbedPane.class);
    myComponentClassMap.put(JSplitPane.class.getName(), RadSplitPane.class);
    myComponentClassMap.put(JToolBar.class.getName(), RadToolBar.class);
    myComponentClassMap.put(JTable.class.getName(), RadTable.class);
  }

  public InsertComponentProcessor(@NotNull final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
    myPaletteManager = PaletteManager.getInstance(editor.getProject());
  }

  public boolean isSticky() {
    return mySticky;
  }

  public void setSticky(final boolean sticky) {
    mySticky = sticky;
  }

  public void setComponentToInsert(final ComponentItem componentToInsert) {
    myComponentToInsert = componentToInsert;
  }

  public void setLastLocation(final DropLocation location) {
    if (location.canDrop(getComponentToInsert())) {
      myLastLocation = location;
    }
    else {
      DropLocation locationToRight = location.getAdjacentLocation(DropLocation.Direction.RIGHT);
      DropLocation locationToBottom = location.getAdjacentLocation(DropLocation.Direction.DOWN);
      if (locationToRight != null && locationToRight.canDrop(getComponentToInsert())) {
        myLastLocation = locationToRight;
      }
      else if (locationToBottom != null && locationToBottom.canDrop(getComponentToInsert())) {
        myLastLocation = locationToBottom;
      }
      else {
        myLastLocation = location;
      }
    }

    if (myLastLocation.canDrop(getComponentToInsert())) {
      myLastLocation.placeFeedback(myEditor.getActiveDecorationLayer(), getComponentToInsert());
    }
  }

  protected void processKeyEvent(final KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        if (myLastLocation != null) {
          myEditor.getMainProcessor().stopCurrentProcessor();
          processComponentInsert(getComponentToInsert(), myLastLocation);
        }
      }
      else {
        myLastLocation = moveDropLocation(myEditor, myLastLocation, getComponentToInsert(), e);
      }
    }
  }

  @NotNull
  public static String suggestBinding(final RadRootContainer rootContainer, @NotNull final String componentClassName) {
    String shortClassName = getShortClassName(componentClassName);

    LOG.assertTrue(shortClassName.length() > 0);

    return getUniqueBinding(rootContainer, shortClassName);
  }

  public static String getShortClassName(@NonNls final String componentClassName) {
    final int lastDotIndex = componentClassName.lastIndexOf('.');
    String shortClassName = componentClassName.substring(lastDotIndex + 1);

    // Here is euristic. Chop first 'J' letter for standard Swing classes.
    // Without 'J' bindings look better.
    if(
      shortClassName.length() > 1 && Character.isUpperCase(shortClassName.charAt(1)) &&
      componentClassName.startsWith("javax.swing.") &&
      StringUtil.startsWithChar(shortClassName, 'J')
    ){
      shortClassName = shortClassName.substring(1);
    }
    shortClassName = StringUtil.decapitalize(shortClassName);
    return shortClassName;
  }

  public static String getUniqueBinding(RadRootContainer root, final String baseName) {
    // Generate member name based on current code style
    //noinspection ForLoopThatDoesntUseLoopVariable
    for(int i = 0; true; i++){
      final String nameCandidate = baseName + (i + 1);
      final String binding = CodeStyleManager.getInstance(root.getModule().getProject()).propertyNameToVariableName(
        nameCandidate,
        VariableKind.FIELD
      );

      if (FormEditingUtil.findComponentWithBinding(root, binding) == null) {
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
    final ComponentItem item = Palette.getInstance(editor.getProject()).getItem(insertedComponent.getComponentClassName());
    if ((item != null && item.isAutoCreateBinding()) || insertedComponent.isCustomCreateRequired()) {
      doCreateBindingWhenDrop(editor, insertedComponent);
    }
  }

  private static void doCreateBindingWhenDrop(final GuiEditor editor, final RadComponent insertedComponent) {
    // Now if the inserted component is a input control, we need to automatically create binding
    final String binding = suggestBinding(editor.getRootContainer(), insertedComponent.getComponentClassName());
    insertedComponent.setBinding(binding);
    insertedComponent.setDefaultBinding(true);

    createBindingField(editor, insertedComponent);
  }

  public static void createBindingField(final GuiEditor editor, final RadComponent insertedComponent) {
    // Try to create field in the corresponding bound class
    final String classToBind = editor.getRootContainer().getClassToBind();
    if(classToBind != null){
      final PsiClass aClass = FormEditingUtil.findClassToBind(editor.getModule(), classToBind);
      if(aClass != null && aClass.findFieldByName(insertedComponent.getBinding(), true) == null) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              CreateFieldFix.runImpl(editor.getProject(),
                                     editor.getRootContainer(),
                                     aClass,
                                     insertedComponent.getComponentClassName(),
                                     insertedComponent.getBinding(),
                                     false, // silently skip all errors (if any)
                                     null);
            }
          }
        );
      }
    }
  }

  protected void processMouseEvent(final MouseEvent e){
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      final ComponentItem componentItem = getComponentToInsert();
      if (componentItem != null) {
        processComponentInsert(e.getPoint(), null, componentItem);
      }
    }
    else if (e.getID() == MouseEvent.MOUSE_MOVED) {
      final ComponentItem componentToInsert = getComponentToInsert();
      if (componentToInsert != null) {
        myLastLocation = myGridInsertProcessor.processDragEvent(e.getPoint(), componentToInsert);
        if (myLastLocation.canDrop(getComponentToInsert())) {
          setCursor(FormEditingUtil.getCopyDropCursor());
        }
        else {
          setCursor(FormEditingUtil.getMoveNoDropCursor());
        }
      }
    }
  }

  private ComponentItem getComponentToInsert() {
    return (myComponentToInsert != null)
           ? myComponentToInsert
           : myPaletteManager.getActiveItem(ComponentItem.class);
  }

  // either point or targetContainer is null
  public void processComponentInsert(@Nullable final Point point, @Nullable final RadContainer targetContainer, final ComponentItem item) {
    final DropLocation location = (point != null)
                                  ? GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), point)
                                  : new GridDropLocation(targetContainer, 0, 0);

    processComponentInsert(item, location);
  }

  public void processComponentInsert(final ComponentItem item, final DropLocation location) {
    myEditor.getActiveDecorationLayer().removeFeedback();
    myEditor.setDesignTimeInsets(2);

    if (!validateNestedFormInsert(item)) {
      return;
    }

    if (!checkAddDependencyOnInsert(item)) {
      return;
    }

    if (!myEditor.ensureEditable()) {
      return;
    }

    myInsertedComponent = createInsertedComponent(myEditor, item);
    if (myInsertedComponent == null) {
      return;
    }

    if (location.canDrop(item)) {
      CommandProcessor.getInstance().executeCommand(
        myEditor.getProject(),
        new Runnable(){
          public void run(){
            createBindingWhenDrop(myEditor, myInsertedComponent);

            final RadComponent[] components = new RadComponent[]{myInsertedComponent};
            location.processDrop(myEditor, components, null, item);

            FormEditingUtil.selectSingleComponent(myEditor, myInsertedComponent);

            if (location.getContainer() != null && location.getContainer().isXY()) {
              Dimension newSize = myInsertedComponent.getPreferredSize();
              Util.adjustSize(myInsertedComponent.getDelegee(), myInsertedComponent.getConstraints(), newSize);
              myInsertedComponent.setSize(newSize);
            }

            if (myInsertedComponent.getParent() instanceof RadRootContainer &&
                myInsertedComponent instanceof RadAtomicComponent) {
              GridBuildUtil.convertToGrid(myEditor);
              FormEditingUtil.selectSingleComponent(myEditor, myInsertedComponent);
            }

            checkBindTopLevelPanel();

            if (!mySticky) {
              PaletteManager.getInstance(myEditor.getProject()).clearActiveItem();
            }

            myEditor.refreshAndSave(false);
          }

        },
        null,
        null
      );
    }
    myComponentToInsert = null;
  }

  private boolean checkAddDependencyOnInsert(final ComponentItem item) {
    if (item.getClassName().equals(HSpacer.class.getName()) || item.getClassName().equals(VSpacer.class.getName())) {
      // this is mostly required for IDEA developers, so that developers don't receive prompt to offer ui-designer-impl dependency
      return true;
    }
    PsiManager manager = PsiManager.getInstance(myEditor.getProject());
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(myEditor.getProject());
    final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myEditor.getModule());
    final PsiClass componentClass = manager.findClass(item.getClassName(), projectScope);
    if (componentClass != null && manager.findClass(item.getClassName(), moduleScope) == null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myEditor.getProject()).getFileIndex();
      List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(componentClass.getContainingFile().getVirtualFile());
      if (entries.size() > 0) {
        if (entries.get(0) instanceof ModuleSourceOrderEntry) {
          if (!checkAddModuleDependency(item, (ModuleSourceOrderEntry) entries.get(0))) return false;
        }
        else if (entries.get(0) instanceof LibraryOrderEntry) {
          if (!checkAddLibraryDependency(item, (LibraryOrderEntry) entries.get(0))) return false;
        }
      }
    }
    return true;
  }

  private boolean checkAddModuleDependency(final ComponentItem item, final ModuleSourceOrderEntry moduleSourceOrderEntry) {
    final Module ownerModule = moduleSourceOrderEntry.getOwnerModule();
    int rc = Messages.showYesNoCancelDialog(
      myEditor,
      UIDesignerBundle.message("add.module.dependency.prompt", item.getClassName(), ownerModule.getName(), myEditor.getModule().getName()),
      UIDesignerBundle.message("add.module.dependency.title"),
      Messages.getQuestionIcon());
    if (rc == 2) return false;
    if (rc == 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final ModifiableRootModel model = ModuleRootManager.getInstance(myEditor.getModule()).getModifiableModel();
          model.addModuleOrderEntry(ownerModule);
          model.commit();
        }
      });
    }
    return true;
  }

  private boolean checkAddLibraryDependency(final ComponentItem item, final LibraryOrderEntry libraryOrderEntry) {
    int rc = Messages.showYesNoCancelDialog(
      myEditor,
      UIDesignerBundle.message("add.library.dependency.prompt", item.getClassName(), libraryOrderEntry.getPresentableName(), myEditor.getModule().getName()),
      UIDesignerBundle.message("add.library.dependency.title"),
      Messages.getQuestionIcon());
    if (rc == 2) return false;
    if (rc == 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final ModifiableRootModel model = ModuleRootManager.getInstance(myEditor.getModule()).getModifiableModel();
          if (libraryOrderEntry.isModuleLevel()) {
            copyModuleLevelLibrary(libraryOrderEntry.getLibrary(), model);
          }
          else {
            model.addLibraryEntry(libraryOrderEntry.getLibrary());
          }
          model.commit();
        }
      });
    }
    return true;
  }

  private static void copyModuleLevelLibrary(final Library fromLibrary, final ModifiableRootModel toModel) {
    final LibraryTable.ModifiableModel libraryTableModel = toModel.getModuleLibraryTable().getModifiableModel();
    Library library = libraryTableModel.createLibrary(null);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for(OrderRootType rootType: OrderRootType.ALL_TYPES) {
      for(String url: fromLibrary.getUrls(rootType)) {
        libraryModel.addRoot(url, rootType);
      }
    }
    libraryModel.commit();
    libraryTableModel.commit();
  }

  private boolean validateNestedFormInsert(final ComponentItem item) {
    PsiFile boundForm = item.getBoundForm();
    if (boundForm != null) {
      try {
        Utils.validateNestedFormLoop(FormEditingUtil.buildResourceName(boundForm), new PsiNestedFormLoader(myEditor.getModule()));
      }
      catch(Exception ex) {
        Messages.showErrorDialog(myEditor, ex.getMessage(), CommonBundle.getErrorTitle());
        return false;
      }
    }
    return true;
  }

  public static RadContainer createPanelComponent(GuiEditor editor) {
    RadComponent c = createInsertedComponent(editor, Palette.getInstance(editor.getProject()).getPanelItem());
    LOG.assertTrue(c != null);
    return (RadContainer) c;
  }

  @Nullable
  public static RadComponent createInsertedComponent(GuiEditor editor, ComponentItem item) {
    if (item.isAnyComponent()) {
      ComponentItem newItem = item.clone();
      ComponentItemDialog dlg = new ComponentItemDialog(editor.getProject(), editor, newItem, true);
      dlg.setTitle(UIDesignerBundle.message("palette.non.palette.component.title"));
      dlg.show();
      if(!dlg.isOK()) {
        return null;
      }

      item = newItem;
    }

    RadComponent result;
    final String id = FormEditingUtil.generateId(editor.getRootContainer());

    Class radComponentClass = getRadComponentClass(item.getClassName());
    if (radComponentClass != null) {
      try {
        result = (RadComponent) radComponentClass.getConstructor(Module.class, String.class).newInstance(editor.getModule(), id);
      }
      catch (Exception e) {
        LOG.error(e);
        return null;
      }
    }
    else {
      PsiFile boundForm = item.getBoundForm();
      if (boundForm != null) {
        try {
          result = new RadNestedForm(editor.getModule(), FormEditingUtil.buildResourceName(boundForm), id);
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
          String errorDescription = Utils.validateJComponentClass(loader, item.getClassName(), true);
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
    result.init(editor, item);
    return result;
  }

  public static Class<? extends RadComponent> getRadComponentClass(final String className) {
    return myComponentClassMap.get(className);
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
    return true;
  }

  public Cursor processMouseMoveEvent(final MouseEvent e) {
    final ComponentItem componentItem = myPaletteManager.getActiveItem(ComponentItem.class);
    if (componentItem != null) {
      return myGridInsertProcessor.processMouseMoveEvent(e.getPoint(), false, componentItem);
    }
    return FormEditingUtil.getMoveNoDropCursor();
  }

  @Override public boolean needMousePressed() {
    return true;
  }
}
