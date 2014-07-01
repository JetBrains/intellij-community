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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Dmitry Batkovich
*/
class NewPropertyAction extends AnAction {
  public NewPropertyAction() {
    super("New Property", null, AllIcons.General.Add);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) {
      return;
    }
    final FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
    if (editor == null || !(editor instanceof ResourceBundleEditor)) {
      return;
    }
    final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)editor;

    final String prefix;
    final String separator;
    final String place = e.getPlace();
    if (ActionPlaces.STRUCTURE_VIEW_TOOLBAR.equals(place)) {
      prefix = null;
      separator = null;
    } else {
      final ResourceBundleEditorViewElement selectedElement = resourceBundleEditor.getSelectedElement();
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
    final ResourceBundle resourceBundle = resourceBundleEditor.getResourceBundle();

    Messages.showInputDialog(project,
                             PropertiesBundle.message("new.property.dialog.name.prompt.text"),
                             PropertiesBundle.message("new.property.dialog.title"),
                             Messages.getQuestionIcon(),
                             null,
                             new NewPropertyNameValidator(resourceBundle, prefix, separator));
  }

  private static class NewPropertyNameValidator implements InputValidator {
    private final @NotNull ResourceBundle myResourceBundle;
    private final @Nullable String myPrefix;
    private final @Nullable String mySeparator;


    public NewPropertyNameValidator(final @NotNull ResourceBundle resourceBundle,
                                    final @Nullable String prefix,
                                    final @Nullable String separator) {
      myResourceBundle = resourceBundle;
      myPrefix = prefix;
      mySeparator = separator;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return true;
    }

    @Override
    public boolean canClose(final String inputString) {
      final String newPropertyName = myPrefix == null ? inputString : (myPrefix + mySeparator + inputString);

      for (final PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
        for (final String propertyName : propertiesFile.getNamesMap().keySet()) {
          if (newPropertyName.equals(propertyName)) {
            Messages.showErrorDialog("Can't add new property. Property with key \'" + newPropertyName + "\' already exists.", "New Property");
            return false;
          }
        }
      }

      final PropertiesFile defaultPropertiesFile = myResourceBundle.getDefaultPropertiesFile();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            @Override
            public void run() {
              defaultPropertiesFile.addProperty(newPropertyName, "");
            }
          });
        }
      });
      return true;
    }
  }
}
