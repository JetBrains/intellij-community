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
package com.intellij.lang.properties.editor.actions;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author VISTALL
 * @since 22:19/28.03.13
 */
public class AddPropertyKeyAction extends AnAction {
  private final ResourceBundle myResourceBundle;

  public AddPropertyKeyAction(@NotNull ResourceBundle resourceBundle) {
    super(CommonBundle.message("button.add"), PropertiesBundle.message("button.add.property.description"), IconUtil.getAddIcon());
    myResourceBundle = resourceBundle;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final List<PropertiesFile> propertiesFiles = myResourceBundle.getPropertiesFiles(project);

    final String message = Messages
      .showInputDialog(project, PropertiesBundle.message("add.dialog.property.name"), PropertiesBundle.message("add.dialog.property.title"),
                       AllIcons.General.QuestionDialog, null, new InputValidator() {
        @Override
        public boolean checkInput(String inputString) {
          if (StringUtil.isEmptyOrSpaces(inputString)) {
            return false;
          }

          boolean hasInAll = true;
          for (PropertiesFile propertiesFile : propertiesFiles) {
            if (propertiesFile.findPropertyByKey(inputString) == null) {
              hasInAll = false;
            }
          }
          return !hasInAll && !inputString.contains(" ");
        }

        @Override
        public boolean canClose(String inputString) {
          return checkInput(inputString);
        }
      });

    if (message == null) {
      return;
    }


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (PropertiesFile propertiesFile : propertiesFiles) {
          final String temp = message.trim();
          propertiesFile.addProperty(temp, temp);
        }
      }
    });
  }
}
