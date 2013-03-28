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
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author VISTALL
 * @since 22:30/28.03.13
 */
public class RemovePropertyKeyAction extends AnAction {
  @NotNull private final ResourceBundle myBundle;
  @NotNull private final Tree myTree;

  public RemovePropertyKeyAction(@NotNull ResourceBundle bundle, @NotNull Tree tree) {
    super(CommonBundle.message("button.remove"), PropertiesBundle.message("remove.property.intention.text"), IconUtil.getRemoveIcon());
    myBundle = bundle;
    myTree = tree;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final List<StructureViewComponent.StructureViewTreeElementWrapper> treeElements =
      TreeUtil.collectSelectedObjectsOfType(myTree, StructureViewComponent.StructureViewTreeElementWrapper.class);

    String joinedProperties = StringUtil.join(treeElements, new Function<StructureViewComponent.StructureViewTreeElementWrapper, String>() {
      @Override
      public String fun(StructureViewComponent.StructureViewTreeElementWrapper temp) {
        return temp.getName();
      }
    }, ", ");

    final int result = Messages.showYesNoDialog(project, PropertiesBundle.message("remove.dialog.confirm.description", joinedProperties),
                                                PropertiesBundle.message("remove.dialog.confirm.title"), AllIcons.General.QuestionDialog);
    if (result != Messages.OK) {
      return;
    }

    ApplicationManager.getApplication().

      runWriteAction(new Runnable() {
        @Override
        public void run() {
          final List<PropertiesFile> propertiesFiles = myBundle.getPropertiesFiles(project);
          for (PropertiesFile propertiesFile : propertiesFiles) {
            for (StructureViewComponent.StructureViewTreeElementWrapper treeElement : treeElements) {
              propertiesFile.removeProperties(treeElement.getName());
            }
          }
        }
      });
  }
}
