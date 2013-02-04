/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.properties.refactoring;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.ResourceBundleEditor;
import com.intellij.lang.properties.editor.ResourceBundleUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Encapsulates logic of renaming resource bundle property key.
 * 
 * @author Denis Zhdanov
 * @since 11/9/10 4:13 PM
 */
public class ResourceBundleKeyRenameHandler implements RenameHandler {

  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    ResourceBundleEditor editor = ResourceBundleUtil.getEditor(dataContext);
    if (editor == null) {
      return false;
    }
    return editor.getState(FileEditorStateLevel.NAVIGATION).getPropertyName() != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    ResourceBundleEditor bundleEditor = ResourceBundleUtil.getEditor(dataContext);
    if (bundleEditor == null) {
      return;
    }

    String propertyName = bundleEditor.getState(FileEditorStateLevel.NAVIGATION).getPropertyName();
    if (propertyName == null) {
      return;
    }

    ResourceBundle bundle = ResourceBundleUtil.getResourceBundleFromDataContext(dataContext);
    if (bundle == null) {
      return;
    }
    Messages.showInputDialog(project, PropertiesBundle.message("rename.bundle.enter.new.resource.bundle.key.name.prompt.text"),
                             PropertiesBundle.message("rename.resource.bundle.key.dialog.title"), Messages.getQuestionIcon(), propertyName,
                             new ResourceBundleKeyRenameValidator(project, bundleEditor, bundle, propertyName));
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
  
  private static class ResourceBundleKeyRenameValidator implements InputValidator {

    private final Set<String> myExistingProperties = new HashSet<String>();
    
    private final ResourceBundleEditor myEditor;
    private final String               myOldPropertyName;

    ResourceBundleKeyRenameValidator(Project project, ResourceBundleEditor editor, ResourceBundle bundle, String oldPropertyName) {
      myEditor = editor;
      myOldPropertyName = oldPropertyName;
      for (PropertiesFile file : bundle.getPropertiesFiles(project)) {
        for (IProperty property : file.getProperties()) {
          myExistingProperties.add(property.getKey());
        }
      }
      myExistingProperties.remove(oldPropertyName);
    }

    @Override
    public boolean checkInput(String inputString) {
      return inputString != null && !inputString.isEmpty() && !myExistingProperties.contains(inputString);
    }

    @Override
    public boolean canClose(final String inputString) {
      if (!checkInput(inputString)) {
        return false;
      }
      
      if (myOldPropertyName.equals(inputString)) {
        return true;
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myEditor.renameProperty(myOldPropertyName, inputString);
        }
      });
      return true;
    }
  }
}
