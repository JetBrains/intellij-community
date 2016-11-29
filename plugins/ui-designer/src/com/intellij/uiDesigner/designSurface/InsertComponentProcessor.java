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
package com.intellij.uiDesigner.designSurface;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
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
import java.awt.*;
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

  private final GuiEditor myEditor;
  private boolean mySticky;
  private RadComponent myInsertedComponent;
  private final GridInsertProcessor myGridInsertProcessor;
  private ComponentItem myComponentToInsert;
  private ComponentDropLocation myLastLocation;

  private static final Map<String, RadComponentFactory> myComponentClassMap = new HashMap<>();

  static {
    myComponentClassMap.put(JScrollPane.class.getName(), new RadScrollPane.Factory());
    myComponentClassMap.put(JPanel.class.getName(), new RadContainer.Factory());
    myComponentClassMap.put(VSpacer.class.getName(), new RadVSpacer.Factory());
    myComponentClassMap.put(HSpacer.class.getName(), new RadHSpacer.Factory());
    myComponentClassMap.put(JTabbedPane.class.getName(), new RadTabbedPane.Factory());
    myComponentClassMap.put(JSplitPane.class.getName(), new RadSplitPane.Factory());
    myComponentClassMap.put(JToolBar.class.getName(), new RadToolBar.Factory());
    myComponentClassMap.put(JTable.class.getName(), new RadTable.Factory());
  }

  public InsertComponentProcessor(@NotNull final GuiEditor editor) {
    myEditor = editor;
    myGridInsertProcessor = new GridInsertProcessor(editor);
  }

  public void setSticky(final boolean sticky) {
    mySticky = sticky;
  }

  public void setComponentToInsert(final ComponentItem componentToInsert) {
    myComponentToInsert = componentToInsert;
  }

  public void setLastLocation(final ComponentDropLocation location) {
    final ComponentItem componentToInsert = getComponentToInsert();
    assert componentToInsert != null;
    ComponentItemDragObject dragObject = new ComponentItemDragObject(componentToInsert);
    if (location.canDrop(dragObject)) {
      myLastLocation = location;
    }
    else {
      ComponentDropLocation locationToRight = location.getAdjacentLocation(ComponentDropLocation.Direction.RIGHT);
      ComponentDropLocation locationToBottom = location.getAdjacentLocation(ComponentDropLocation.Direction.DOWN);
      if (locationToRight != null && locationToRight.canDrop(dragObject)) {
        myLastLocation = locationToRight;
      }
      else if (locationToBottom != null && locationToBottom.canDrop(dragObject)) {
        myLastLocation = locationToBottom;
      }
      else {
        myLastLocation = location;
      }
    }

    if (myLastLocation.canDrop(dragObject)) {
      myLastLocation.placeFeedback(myEditor.getActiveDecorationLayer(), dragObject);
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
        ComponentItem componentToInsert = getComponentToInsert();
        if (componentToInsert == null) {
          cancelOperation();
        }
        else {
          myLastLocation = moveDropLocation(myEditor, myLastLocation, new ComponentItemDragObject(componentToInsert), e);
        }
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
    if (
      shortClassName.length() > 1 && Character.isUpperCase(shortClassName.charAt(1)) &&
      componentClassName.startsWith("javax.swing.") &&
      StringUtil.startsWithChar(shortClassName, 'J')
      ) {
      shortClassName = shortClassName.substring(1);
    }
    shortClassName = StringUtil.decapitalize(shortClassName);
    return shortClassName;
  }

  public static String getUniqueBinding(RadRootContainer root, final String baseName) {
    // Generate member name based on current code style
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 0; true; i++) {
      final String nameCandidate = baseName + (i + 1);
      final String binding = JavaCodeStyleManager.getInstance(root.getProject()).propertyNameToVariableName(
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
   *
   * @param editor
   * @param insertedComponent
   * @param forceBinding
   */
  public static void createBindingWhenDrop(final GuiEditor editor, final RadComponent insertedComponent, final boolean forceBinding) {
    final ComponentItem item = Palette.getInstance(editor.getProject()).getItem(insertedComponent.getComponentClassName());
    if ((item != null && item.isAutoCreateBinding()) || insertedComponent.isCustomCreateRequired() || forceBinding) {
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
    if (classToBind != null) {
      final PsiClass aClass = FormEditingUtil.findClassToBind(editor.getModule(), classToBind);
      if (aClass != null && aClass.findFieldByName(insertedComponent.getBinding(), true) == null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(aClass)) {
          return;
        }
        ApplicationManager.getApplication().runWriteAction(
          () -> CreateFieldFix.runImpl(editor.getProject(),
                                     editor.getRootContainer(),
                                     aClass,
                                     insertedComponent.getComponentClassName(),
                                     insertedComponent.getBinding(),
                                     false, // silently skip all errors (if any)
                                     null)
        );
      }
    }
  }

  protected void processMouseEvent(final MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      final ComponentItem componentItem = getComponentToInsert();
      if (componentItem != null) {
        processComponentInsert(e.getPoint(), componentItem);
      }
    }
    else if (e.getID() == MouseEvent.MOUSE_MOVED) {
      final ComponentItem componentToInsert = getComponentToInsert();
      if (componentToInsert != null) {
        ComponentItemDragObject dragObject = new ComponentItemDragObject(componentToInsert);
        myLastLocation = myGridInsertProcessor.processDragEvent(e.getPoint(), dragObject);
        if (myLastLocation.canDrop(dragObject)) {
          setCursor(FormEditingUtil.getCopyDropCursor());
        }
        else {
          setCursor(FormEditingUtil.getMoveNoDropCursor());
        }
      }
    }
  }

  @Nullable
  private ComponentItem getComponentToInsert() {
    return (myComponentToInsert != null)
           ? myComponentToInsert
           : PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class);
  }

  public void processComponentInsert(@NotNull final Point point, final ComponentItem item) {
    final ComponentDropLocation location = GridInsertProcessor.getDropLocation(myEditor.getRootContainer(), point);
    processComponentInsert(item, location);
  }

  public void processComponentInsert(ComponentItem item, final ComponentDropLocation location) {
    myEditor.getActiveDecorationLayer().removeFeedback();
    myEditor.setDesignTimeInsets(2);

    item = replaceAnyComponentItem(myEditor, item, UIDesignerBundle.message("palette.non.palette.component.title"));
    if (item == null) {
      return;
    }

    if (!validateNestedFormInsert(item)) {
      return;
    }

    if (!checkAddDependencyOnInsert(item)) {
      return;
    }

    if (!myEditor.ensureEditable()) {
      return;
    }

    final boolean forceBinding = item.isAutoCreateBinding();
    myInsertedComponent = createInsertedComponent(myEditor, item);
    setCursor(Cursor.getDefaultCursor());
    if (myInsertedComponent == null) {
      if (!mySticky) {
        PaletteToolWindowManager.getInstance(myEditor).clearActiveItem();
      }
      return;
    }

    final ComponentItemDragObject dragObject = new ComponentItemDragObject(item);
    if (location.canDrop(dragObject)) {
      CommandProcessor.getInstance().executeCommand(
        myEditor.getProject(),
        () -> {
          createBindingWhenDrop(myEditor, myInsertedComponent, forceBinding);

          final RadComponent[] components = new RadComponent[]{myInsertedComponent};
          location.processDrop(myEditor, components, null, dragObject);

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
            PaletteToolWindowManager.getInstance(myEditor).clearActiveItem();
          }

          myEditor.refreshAndSave(false);
        }, UIDesignerBundle.message("command.insert.component"), null);
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
    final PsiClass componentClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(item.getClassName(), projectScope);
    if (componentClass != null && JavaPsiFacade.getInstance(manager.getProject()).findClass(item.getClassName(), moduleScope) == null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myEditor.getProject()).getFileIndex();
      List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(componentClass.getContainingFile().getVirtualFile());
      if (entries.size() > 0) {
        if (entries.get(0) instanceof ModuleSourceOrderEntry) {
          if (!checkAddModuleDependency(item, (ModuleSourceOrderEntry)entries.get(0))) return false;
        }
        else if (entries.get(0) instanceof LibraryOrderEntry) {
          if (!checkAddLibraryDependency(item, (LibraryOrderEntry)entries.get(0))) return false;
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
    if (rc == Messages.CANCEL) return false;
    if (rc == Messages.YES) {
      ModuleRootModificationUtil.addDependency(myEditor.getModule(), ownerModule);
    }
    return true;
  }

  private boolean checkAddLibraryDependency(final ComponentItem item, final LibraryOrderEntry libraryOrderEntry) {
    int rc = Messages.showYesNoCancelDialog(
      myEditor,
      UIDesignerBundle.message("add.library.dependency.prompt", item.getClassName(), libraryOrderEntry.getPresentableName(),
                               myEditor.getModule().getName()),
      UIDesignerBundle.message("add.library.dependency.title"),
      Messages.getQuestionIcon());
    if (rc == Messages.CANCEL) return false;
    if (rc == Messages.YES) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myEditor.getModule()).getModifiableModel();
        if (libraryOrderEntry.isModuleLevel()) {
          copyModuleLevelLibrary(libraryOrderEntry.getLibrary(), model);
        }
        else {
          model.addLibraryEntry(libraryOrderEntry.getLibrary());
        }
        model.commit();
      });
    }
    return true;
  }

  private static void copyModuleLevelLibrary(final Library fromLibrary, final ModifiableRootModel toModel) {
    final LibraryTable.ModifiableModel libraryTableModel = toModel.getModuleLibraryTable().getModifiableModel();
    Library library = libraryTableModel.createLibrary(null);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      for (String url : fromLibrary.getUrls(rootType)) {
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
        final String formName = FormEditingUtil.buildResourceName(boundForm);
        final String targetForm = FormEditingUtil.buildResourceName(myEditor.getPsiFile());
        Utils.validateNestedFormLoop(formName, new PsiNestedFormLoader(myEditor.getModule()), targetForm);
      }
      catch (Exception ex) {
        Messages.showErrorDialog(myEditor, ex.getMessage(), CommonBundle.getErrorTitle());
        return false;
      }
    }
    return true;
  }

  public static RadContainer createPanelComponent(GuiEditor editor) {
    RadComponent c = createInsertedComponent(editor, Palette.getInstance(editor.getProject()).getPanelItem());
    LOG.assertTrue(c != null);
    return (RadContainer)c;
  }

  @Nullable
  public static ComponentItem replaceAnyComponentItem(GuiEditor editor, ComponentItem item, final String title) {
    if (item.isAnyComponent()) {
      ComponentItem newItem = item.clone();
      ComponentItemDialog dlg = new ComponentItemDialog(editor.getProject(), editor, newItem, true);
      dlg.setTitle(title);
      if (!dlg.showAndGet()) {
        return null;
      }

      return newItem;
    }
    return item;
  }

  @Nullable
  public static RadComponent createInsertedComponent(GuiEditor editor, ComponentItem item) {
    RadComponent result;
    final String id = FormEditingUtil.generateId(editor.getRootContainer());

    final ClassLoader loader = LoaderFactory.getInstance(editor.getProject()).getLoader(editor.getFile());
    RadComponentFactory factory = getRadComponentFactory(item.getClassName(), loader);
    if (factory != null) {
      try {
        result = factory.newInstance(editor, item.getClassName(), id);
      }
      catch (Exception e) {
        LOG.error(e);
        return null;
      }
    }
    else {
      PsiFile boundForm = item.getBoundForm();
      if (boundForm != null) {
        final String formFileName = FormEditingUtil.buildResourceName(boundForm);
        try {
          result = new RadNestedForm(editor, formFileName, id);
        }
        catch (Exception ex) {
          String errorMessage = UIDesignerBundle.message("error.instantiating.nested.form", formFileName,
                                                         (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
          result = RadErrorComponent.create(
            editor,
            id,
            item.getClassName(),
            null,
            errorMessage
          );
        }
      }
      else {
        try {
          final Class aClass = Class.forName(item.getClassName(), true, loader);
          if (item.isContainer()) {
            LOG.debug("Creating custom container instance");
            result = new RadContainer(editor, aClass, id);
          }
          else {
            result = new RadAtomicComponent(editor, aClass, id);
          }
        }
        catch (final UnsupportedClassVersionError ucve) {
          result = RadErrorComponent.create(editor, id, item.getClassName(), null,
                                            UIDesignerBundle.message("unsupported.component.class.version")
          );
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
            editor,
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

  @Nullable
  public static RadComponentFactory getRadComponentFactory(final Project project, final String className) {
    ClassLoader loader = ApplicationManager.getApplication().runReadAction(new Computable<ClassLoader>() {
      @Override
      public ClassLoader compute() {
        return LoaderFactory.getInstance(project).getProjectClassLoader();
      }
    });
    return getRadComponentFactory(className, loader);
  }

  @Nullable
  private static RadComponentFactory getRadComponentFactory(final String className, final ClassLoader loader) {
    Class componentClass;
    try {
      componentClass = Class.forName(className, false, loader);
    }
    catch (ClassNotFoundException e) {
      return myComponentClassMap.get(className);
    }
    return getRadComponentFactory(componentClass);
  }

  @Nullable
  public static RadComponentFactory getRadComponentFactory(Class componentClass) {
    while (componentClass != null) {
      RadComponentFactory c = myComponentClassMap.get(componentClass.getName());
      if (c != null) return c;
      componentClass = componentClass.getSuperclass();
      // if a component item is a JPanel subclass, a RadContainer should be created for it only
      // if it's marked as "Is Container"
      if (JPanel.class.equals(componentClass)) return null;
    }
    return null;
  }

  private void checkBindTopLevelPanel() {
    if (myEditor.getRootContainer().getComponentCount() == 1) {
      final RadComponent component = myEditor.getRootContainer().getComponent(0);
      if (component.getBinding() == null) {
        if (component == myInsertedComponent ||
            (component instanceof RadContainer && ((RadContainer)component).getComponentCount() == 1 &&
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
    final ComponentItem componentItem = PaletteToolWindowManager.getInstance(myEditor).getActiveItem(ComponentItem.class);
    if (componentItem != null) {
      return myGridInsertProcessor.processMouseMoveEvent(e.getPoint(), false, new ComponentItemDragObject(componentItem));
    }
    return FormEditingUtil.getMoveNoDropCursor();
  }

  @Override
  public boolean needMousePressed() {
    return true;
  }
}
