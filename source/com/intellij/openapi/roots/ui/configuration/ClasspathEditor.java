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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.impl.storage.ClasspathStorageProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.util.IconLoader;
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

  private ClasspathFormatPanel myClasspathFormatPanel;

  public ClasspathEditor(Project project, ModifiableRootModel model, final ModulesProvider modulesProvider) {
    super(project, model);
    myModulesProvider = modulesProvider;
  }

  public boolean isModified() {
    return super.isModified() || ( myClasspathFormatPanel != null && myClasspathFormatPanel.isModified());
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

  public void apply () throws ConfigurationException {
    if(myClasspathFormatPanel!=null) {
      myClasspathFormatPanel.apply();
    }
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

    List<ClasspathStorageProvider> providers = ClasspathStorage.getProviders();
    if(providers.size()>1){
      myClasspathFormatPanel = new ClasspathFormatPanel(providers);
      panel.add(myClasspathFormatPanel, BorderLayout.SOUTH);
    }

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

  private class ClasspathFormatPanel extends JPanel {

    private JComboBox cbClasspathFormat;

    private Map<String,String> formatIdToDescr = new HashMap<String, String>();

    private ClasspathFormatPanel(final List<ClasspathStorageProvider> providers) {
      super(new GridBagLayout());
      add(new JLabel(ProjectBundle.message("project.roots.classpath.format.label")),
                      new GridBagConstraints(0,0,1,1,0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 6, 6, 0), 0, 0));

      for (ClasspathStorageProvider provider : providers){
        formatIdToDescr.put ( provider.getID(), provider.getDescription());
      }

      final Object[] items = formatIdToDescr.values().toArray();
      cbClasspathFormat = new JComboBox(items);
      updateClasspathFormat();
      add(cbClasspathFormat,
                      new GridBagConstraints(1,0,1,1,1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(6, 6, 6, 0), 0, 0));
    }

    private void updateClasspathFormat() {
      cbClasspathFormat.setSelectedItem(formatIdToDescr.get(getModuleClasspathFormat()));
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
      return ClasspathStorage.getStorageType(myModel.getModule());
    }

    boolean isModified () {
      return cbClasspathFormat != null && !getSelectedClasspathFormat().equals(getModuleClasspathFormat());
    }

    void apply () throws ConfigurationException {
      if (isModified()) {
        final String storageID = getSelectedClasspathFormat();
        ClasspathStorage.getProvider(storageID).assertCompatible(myModel);
        ClasspathStorage.setStorageType(myModel.getModule(), storageID);
      }
    }
  }
}
