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
package com.intellij.lang.properties.editor;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.structureView.PropertiesPrefixGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Dmitry Batkovich
*/
class NewPropertyAction extends AnAction {

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
    Messages.showInputDialog(project,
                             PropertiesBundle.message("new.property.dialog.name.prompt.text"),
                             PropertiesBundle.message("new.property.dialog.title"),
                             Messages.getQuestionIcon(),
                             null,
                             new NewPropertyNameValidator(resourceBundleEditor, prefix, separator));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!myEnabledForce) {
      final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
      e.getPresentation().setEnabledAndVisible(editor instanceof ResourceBundleEditor);
    }
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
        for (final String propertyName : propertiesFile.getNamesMap().keySet()) {
          if (newPropertyName.equals(propertyName)) {
            Messages.showErrorDialog("Can't add new property. Property with key \'" + newPropertyName + "\' already exists.", "New Property");
            return false;
          }
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          WriteCommandAction.runWriteCommandAction(resourceBundle.getProject(), new Runnable() {
            @Override
            public void run() {
              myResourceBundleEditor.getPropertiesInsertDeleteManager().insertNewProperty(newPropertyName, "");
            }
          });
        }
      });

      myResourceBundleEditor.updateTreeRoot();
      myResourceBundleEditor.selectProperty(newPropertyName);
      return true;
    }
  }
}
