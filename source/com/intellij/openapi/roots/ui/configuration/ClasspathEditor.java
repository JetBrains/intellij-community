/*
 * Copyright 2004-2005 Alexey Efimov
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.OrderPanelListener;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:54:57 PM
 */
public class ClasspathEditor extends ModuleElementsEditor {
  public static final String NAME = ProjectBundle.message("modules.classpath.title");
  public static final Icon ICON = IconLoader.getIcon("/modules/classpath.png");

  private ClasspathPanel myPanel;
  private ModulesProvider myModulesProvider;

  public ClasspathEditor(Project project, ModifiableRootModel model, final ModulesProvider modulesProvider) {
    super(project, model);
    myModulesProvider = modulesProvider;
  }

  public String getHelpTopic() {
    return "project.paths.order";
  }

  public String getDisplayName() {
    return ClasspathEditor.NAME;
  }

  public Icon getIcon() {
    return ClasspathEditor.ICON;
  }

  public void saveData() {
    myPanel.stopEditing();
    flushChangesToModel();
  }


  public JComponent createComponentImpl() {
    myPanel = new ClasspathPanel(myProject, myModel, myModulesProvider);

    myPanel.addListener(new OrderPanelListener() {
      public void entryMoved() {
        flushChangesToModel();
      }
    });

    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    panel.add(myPanel, BorderLayout.CENTER);

    final ModuleJdkConfigurable jdkConfigurable =
      new ModuleJdkConfigurable(this, myModel, ProjectRootConfigurable.getInstance(myProject).getProjectJdksModel());
    panel.add(jdkConfigurable.createComponent(), BorderLayout.NORTH);
    jdkConfigurable.reset();
    registerDisposable(jdkConfigurable);

    return panel;
  }



  public void flushChangesToModel() {
    List<OrderEntry> entries = myPanel.getEntries();
    myModel.rearrangeOrderEntries(entries.toArray(new OrderEntry[entries.size()]));
  }

  public void moduleStateChanged() {
    if (myPanel != null) {
      myPanel.initFromModel();
    }
  }
}
