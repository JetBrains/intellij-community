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
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.projectImport.eclipse.config.EclipseClasspathStorage;
import com.intellij.ui.OrderPanelListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  private Map<String,String> formatIdToDescr = new HashMap<String, String>();

  private JComboBox cbClasspathFormat;
  private static final String DEFAULT_FORMAT = ModuleRootManagerImpl.StorageChooser.DEFAULT_STORAGE;
  private static final String IDEA_FORMAT_DESCR = ProjectBundle.message("project.roots.classpath.format.default.descr");

  public ClasspathEditor(Project project, ModifiableRootModel model, final ModulesProvider modulesProvider) {
    super(project, model);
    myModulesProvider = modulesProvider;
  }

  public boolean isModified() {
    return super.isModified() || isClasspathFormatModified();
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
    panel.add(createFormatPanel(), BorderLayout.SOUTH);

    return panel;
  }

  private JPanel createFormatPanel() {
    JPanel formatPanel = new JPanel(new GridBagLayout());
    formatPanel.add(new JLabel(ProjectBundle.message("project.roots.classpath.format.label")),
                    new GridBagConstraints(0,0,1,1,0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 6, 6, 0), 0, 0));

    formatIdToDescr.put ( DEFAULT_FORMAT, IDEA_FORMAT_DESCR);
    formatIdToDescr.put ( EclipseClasspathStorage.ID, EclipseClasspathStorage.DESCR);
//    formatIdToDescr.put ( MavenClasspathStorage.ID, MavenClasspathStorage.DESCR);

    final Object[] items = formatIdToDescr.values().toArray();
    cbClasspathFormat = new JComboBox(items);
    cbClasspathFormat.setSelectedItem(formatIdToDescr.get(getModuleClasspathFormat()));
    formatPanel.add(cbClasspathFormat,
                    new GridBagConstraints(1,0,1,1,1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 6, 0), 0, 0));
    return formatPanel;
  }

  private boolean isClasspathFormatModified() {
    return !getSelectedClasspathFormat().equals(getModuleClasspathFormat());
  }

  private String getSelectedClasspathFormat() {
    final String selected = (String)cbClasspathFormat.getSelectedItem();
    for ( Map.Entry<String,String> entry : formatIdToDescr.entrySet() ) {
      if ( entry.getValue().equals(selected)) {
        return entry.getKey();
      }
    }
    throw new IllegalStateException(selected);

  }

  @NotNull
  private String getModuleClasspathFormat() {
    final String optionValue = myModel.getModule().getOptionValue(ModuleRootManagerImpl.StorageChooser.CLASSPATH_OPTION);
    return optionValue == null ? DEFAULT_FORMAT : optionValue;
  }

  private void setModuleClasspathFormat() {
    if (isClasspathFormatModified()) {
      final String format = getSelectedClasspathFormat();
      if ( format.equals(DEFAULT_FORMAT)) {
        myModel.getModule().clearOption(ModuleRootManagerImpl.StorageChooser.CLASSPATH_OPTION);
      } else {
        myModel.getModule().setOption(ModuleRootManagerImpl.StorageChooser.CLASSPATH_OPTION, format);
      }
    }
  }

  public void flushChangesToModel() {
    setModuleClasspathFormat();
    List<OrderEntry> entries = myPanel.getEntries();
    myModel.rearrangeOrderEntries(entries.toArray(new OrderEntry[entries.size()]));
  }

  public void moduleStateChanged() {
    if (myPanel != null) {
      myPanel.initFromModel();
    }
  }
}
