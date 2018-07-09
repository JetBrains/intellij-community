// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.structureView.PropertiesPrefixGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
* @author Dmitry Batkovich
*/
class NewPropertyAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(NewPropertyAction.class);

  private final static String ADD_NEW_PROPERTY_AFTER_SELECTED_PROP = "add.property.after.selected";

  private final boolean myEnabledForce;

  public NewPropertyAction() {
    this(false);
  }

  public NewPropertyAction(final boolean enabledForce) {
    super("New Property", null, AllIcons.General.Add);
    myEnabledForce = enabledForce;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    ResourceBundleEditor resourceBundleEditor;
    final DataContext context = e.getDataContext();
    FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
    if (fileEditor instanceof ResourceBundleEditor) {
      resourceBundleEditor = (ResourceBundleEditor)fileEditor;
    } else {
      final Editor editor = CommonDataKeys.EDITOR.getData(context);
      resourceBundleEditor = editor != null ? editor.getUserData(ResourceBundleEditor.RESOURCE_BUNDLE_EDITOR_KEY) : null;
    }
    if (resourceBundleEditor == null) {
      for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
        if (editor instanceof ResourceBundleEditor) {
          resourceBundleEditor = (ResourceBundleEditor)editor;
        }
      }
      if (resourceBundleEditor == null) {
        return;
      }
    }

    final ResourceBundle bundle = resourceBundleEditor.getResourceBundle();
    final VirtualFile file = bundle.getDefaultPropertiesFile().getVirtualFile();
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file);
    if (status.hasReadonlyFiles()) {
      Messages.showErrorDialog(bundle.getProject(),
                               String.format("Resource bundle '%s' has read-only default properties file", bundle.getBaseName()),
                               "Can't Create New Property");
      return;
    }

    final String prefix;
    final String separator;
    final String place = e.getPlace();
    if (ActionPlaces.STRUCTURE_VIEW_TOOLBAR.equals(place)) {
      prefix = null;
      separator = null;
    } else {
      final ResourceBundleEditorViewElement selectedElement = resourceBundleEditor.getSelectedElementIfOnlyOne();
      if (selectedElement == null) {
        return;
      }
      if (selectedElement instanceof PropertiesPrefixGroup) {
        final PropertiesPrefixGroup group = (PropertiesPrefixGroup)selectedElement;
        prefix = group.getPrefix();
        separator = group.getSeparator();
      }
      else if (selectedElement instanceof ResourceBundlePropertyStructureViewElement ||
               selectedElement instanceof ResourceBundleFileStructureViewElement) {
        prefix = null;
        separator = null;
      }
      else {
        throw new IllegalStateException("unsupported type: " + selectedElement.getClass());
      }
    }

    final ResourceBundlePropertiesUpdateManager propertiesUpdateManager = resourceBundleEditor.getPropertiesInsertDeleteManager();
    final NewPropertyNameValidator nameValidator = new NewPropertyNameValidator(resourceBundleEditor, prefix, separator);
    final String keyToInsert;

    final IProperty anchor;
    IProperty selectedProperty = resourceBundleEditor.getSelectedProperty();
    if (propertiesUpdateManager.isAlphaSorted() || !propertiesUpdateManager.isSorted() || selectedProperty == null) {
      keyToInsert = Messages.showInputDialog(project,
                                             PropertiesBundle.message("new.property.dialog.name.prompt.text"),
                                             PropertiesBundle.message("new.property.dialog.title"),
                                             Messages.getQuestionIcon(),
                                             selectedProperty == null ? getSelectedPrefixText(resourceBundleEditor) : null,
                                             nameValidator);
      anchor = null;
    } else {
      final Pair<String, Boolean> keyNameAndInsertPlaceModification =
        Messages.showInputDialogWithCheckBox(PropertiesBundle.message("new.property.dialog.name.prompt.text"),
                                             PropertiesBundle.message("new.property.dialog.title"),
                                             PropertiesBundle.message("new.property.dialog.checkbox.text"),
                                             PropertiesComponent.getInstance().getBoolean(ADD_NEW_PROPERTY_AFTER_SELECTED_PROP, false),
                                             true,
                                             Messages.getQuestionIcon(),
                                             null,
                                             nameValidator);
      keyToInsert = keyNameAndInsertPlaceModification.getFirst();
      final Boolean insertAfterSelectedProperty = keyNameAndInsertPlaceModification.getSecond();
      PropertiesComponent.getInstance().setValue(ADD_NEW_PROPERTY_AFTER_SELECTED_PROP, insertAfterSelectedProperty, false);
      anchor = insertAfterSelectedProperty ? selectedProperty : null;
    }
    if (keyToInsert != null) {
      final ResourceBundlePropertiesUpdateManager updateManager = resourceBundleEditor.getPropertiesInsertDeleteManager();
      final Runnable insertionAction = () -> {
        if (anchor == null) {
          updateManager.insertNewProperty(keyToInsert, "");
        } else {
          final String anchorKey = anchor.getKey();
          LOG.assertTrue(anchorKey != null);
          updateManager.insertAfter(keyToInsert, "", anchorKey);
        }
      };
      ResourceBundleEditor finalResourceBundleEditor = resourceBundleEditor;
      ApplicationManager.getApplication().runWriteAction(() -> {
        WriteCommandAction.runWriteCommandAction(bundle.getProject(), insertionAction);
        finalResourceBundleEditor.flush();
      });

      resourceBundleEditor.updateTreeRoot();
      resourceBundleEditor.getStructureViewComponent()
        .select(keyToInsert, false)
        .onProcessed(p -> finalResourceBundleEditor.selectProperty(keyToInsert));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!myEnabledForce) {
      final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
      e.getPresentation().setEnabledAndVisible(editor instanceof ResourceBundleEditor);
    }
  }

  @Nullable
  private static String getSelectedPrefixText(@NotNull ResourceBundleEditor resourceBundleEditor) {
    Collection<ResourceBundleEditorViewElement> elements = resourceBundleEditor.getSelectedElements();
    if (elements.size() == 1) {
      ResourceBundleEditorViewElement item = ContainerUtil.getFirstItem(elements);
      if (item instanceof PropertiesPrefixGroup) {
        PropertiesPrefixGroup prefixGroup = (PropertiesPrefixGroup)item;
        return prefixGroup.getPrefix() + prefixGroup.getSeparator();
      }
    }
    return null;
  }

  private static class NewPropertyNameValidator implements InputValidator {
    private final @NotNull ResourceBundleEditor myResourceBundleEditor;
    private final @Nullable String myPrefix;
    private final @Nullable String mySeparator;


    public NewPropertyNameValidator(final @NotNull ResourceBundleEditor resourceBundleEditor,
                                    final @Nullable String prefix,
                                    final @Nullable String separator) {
      myResourceBundleEditor = resourceBundleEditor;
      myPrefix = prefix;
      mySeparator = separator;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return !inputString.isEmpty();
    }

    @Override
    public boolean canClose(final String inputString) {
      final String newPropertyName = myPrefix == null ? inputString : (myPrefix + mySeparator + inputString);

      final ResourceBundle resourceBundle = myResourceBundleEditor.getResourceBundle();
      for (final PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
        IProperty key = propertiesFile.findPropertyByKey(newPropertyName);
        if (key != null) {
          Messages.showErrorDialog("Can't add new property. Property with key \'" + newPropertyName + "\' already exists.", "New Property");
          return false;
        }
      }

      return true;
    }
  }
}
