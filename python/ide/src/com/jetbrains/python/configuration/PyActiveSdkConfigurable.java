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
package com.jetbrains.python.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.NullableConsumer;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class PyActiveSdkConfigurable implements UnnamedConfigurable {
  private JPanel myMainPanel;
  private final Project myProject;
  @Nullable private final Module myModule;
  private MySdkModelListener mySdkModelListener;

  private PyConfigurableInterpreterList myInterpreterList;
  private ProjectSdksModel myProjectSdksModel;
  private ComboBox mySdkCombo;
  private PyInstalledPackagesPanel myPackagesPanel;
  private JButton myDetailsButton;
  private static final String SHOW_ALL = PyBundle.message("active.sdk.dialog.show.all.item");
  private NullableConsumer<Sdk> myDetailsCallback;

  public PyActiveSdkConfigurable(@NotNull Project project) {
    myModule = null;
    myProject = project;
    layoutPanel();
    initContent();
  }

  public PyActiveSdkConfigurable(@NotNull Module module) {
    myModule = module;
    myProject = module.getProject();
    layoutPanel();
    initContent();
  }

  private void initContent() {
    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);

    myProjectSdksModel = myInterpreterList.getModel();
    mySdkModelListener = new MySdkModelListener(this);
    myProjectSdksModel.addListener(mySdkModelListener);

    mySdkCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Sdk selectedSdk = (Sdk)mySdkCombo.getSelectedItem();
        myPackagesPanel.updatePackages(selectedSdk != null ? new PyPackageManagementService(myProject, selectedSdk) : null);
        myPackagesPanel.updateNotifications(selectedSdk);
      }
    });
    myDetailsCallback = new NullableConsumer<Sdk>() {
      @Override
      public void consume(@Nullable Sdk sdk) {
        final Sdk moduleSdk = getSdk();
        if (sdk instanceof PyDetectedSdk) {
          final Sdk addedSdk = SdkConfigurationUtil.setupSdk(myProjectSdksModel.getSdks(), sdk.getHomeDirectory(),
                                                             PythonSdkType.getInstance(), true,
                                                             null, null);
          myProjectSdksModel.addSdk(addedSdk);
          updateSdkList(false);
          if (addedSdk != null)
            mySdkCombo.getModel().setSelectedItem(myProjectSdksModel.findSdk(addedSdk.getName()));
        }
        else if (moduleSdk != null && myProjectSdksModel.findSdk(moduleSdk) != sdk && sdk != null) {
          PythonSdkAdditionalData additionalData = (PythonSdkAdditionalData)sdk.getSdkAdditionalData();
          if (additionalData != null) {
            final String path = additionalData.getAssociatedProjectPath();
            final String basePath = myProject.getBasePath();
            if (basePath != null && !basePath.equals(path))
              additionalData.setAssociatedProjectPath(null);
          }
          updateSdkList(false);
          mySdkCombo.getModel().setSelectedItem(myProjectSdksModel.findSdk(sdk.getName()));
        }
      }
    };
    myDetailsButton.addActionListener(new ActionListener() {
                                        @Override
                                        public void actionPerformed(ActionEvent e) {
                                          PythonSdkDetailsStep.show(myProject, myProjectSdksModel.getSdks(),
                                                                    myModule == null ? new PythonSdkDetailsDialog(myProject, myDetailsCallback) :
                                                                    new PythonSdkDetailsDialog(myModule, myDetailsCallback), myMainPanel,
                                                  myDetailsButton.getLocationOnScreen(),
                                                  new NullableConsumer<Sdk>() {
                                                    @Override
                                                    public void consume(Sdk sdk) {
                                                      if (sdk == null) return;
                                                      final PySdkService sdkService = PySdkService.getInstance();
                                                      sdkService.restoreSdk(sdk);
                                                      if (myProjectSdksModel.findSdk(sdk) == null) {
                                                        myProjectSdksModel.addSdk(sdk);
                                                      }
                                                      updateSdkList(false);
                                                      mySdkCombo.getModel().setSelectedItem(myProjectSdksModel.findSdk(sdk.getName()));
                                                      myPackagesPanel.updatePackages(new PyPackageManagementService(myProject, sdk));
                                                      myPackagesPanel.updateNotifications(sdk);
                                                    }
                                                  }
                                            );
                                        }
                                      }
    );

  }

  private void layoutPanel() {
    final GridBagLayout layout = new GridBagLayout();
    myMainPanel = new JPanel(layout);
    final JLabel interpreterLabel = new JLabel(PyBundle.message("active.sdk.dialog.project.interpreter"));
    final JLabel emptyLabel = new JLabel("  ");
    mySdkCombo = new ComboBox() {
      @Override
      public void setSelectedItem(Object item) {
        if (SHOW_ALL.equals(item)) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              PythonSdkDetailsDialog options = myModule == null ? new PythonSdkDetailsDialog(myProject, myDetailsCallback) :
                                               new PythonSdkDetailsDialog(myModule, myDetailsCallback);
              options.show();
            }
          });
          return;
        }
        if (!PySdkListCellRenderer.SEPARATOR.equals(item))
          super.setSelectedItem(item);
      }
      @Override
      public void paint(Graphics g) {
        try {
          putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
          super.paint(g);
        } finally {
          putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
        }
      }
    };
    mySdkCombo.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
    mySdkCombo.setRenderer(new PySdkListCellRenderer(false));

    final PackagesNotificationPanel notificationsArea = new PackagesNotificationPanel();
    final JComponent notificationsComponent = notificationsArea.getComponent();
    final Dimension preferredSize = mySdkCombo.getPreferredSize();
    mySdkCombo.setPreferredSize(preferredSize);
    notificationsArea.hide();
    myDetailsButton = new FixedSizeButton();
    myDetailsButton.setIcon(PythonIcons.Python.InterpreterGear);
    //noinspection SuspiciousNameCombination
    myDetailsButton.setPreferredSize(new Dimension(preferredSize.height, preferredSize.height));

    myPackagesPanel = new PyInstalledPackagesPanel(myProject, notificationsArea);
    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(2,2,2,2);

    c.gridx = 0;
    c.gridy = 0;
    myMainPanel.add(interpreterLabel, c);

    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 0.1;
    myMainPanel.add(mySdkCombo, c);

    c.insets = new Insets(2,0,2,2);
    c.gridx = 2;
    c.gridy = 0;
    c.weightx = 0.0;
    myMainPanel.add(myDetailsButton, c);

    c.insets = new Insets(2,2,0,2);
    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 3;
    myMainPanel.add(emptyLabel, c);

    c.gridx = 0;
    c.gridy = 2;
    c.weighty = 1.;
    c.gridwidth = 3;
    c.gridheight = GridBagConstraints.RELATIVE;
    c.fill = GridBagConstraints.BOTH;
    myMainPanel.add(myPackagesPanel, c);

    c.gridheight = GridBagConstraints.REMAINDER;
    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 3;
    c.weighty = 0.;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.SOUTH;

    myMainPanel.add(notificationsComponent, c);
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    final Sdk selectedItem = (Sdk)mySdkCombo.getSelectedItem();
    Sdk sdk = getSdk();
    sdk = sdk == null ? null : myProjectSdksModel.findSdk(sdk.getName());
    return selectedItem instanceof PyDetectedSdk || sdk != selectedItem;
  }

  @Nullable
  private Sdk getSdk() {
    if (myModule == null) {
      return ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    return rootManager.getSdk();
  }

  @Override
  public void apply() throws ConfigurationException {
    final Sdk item = (Sdk)mySdkCombo.getSelectedItem();
    Sdk newSdk = item;
    if (item instanceof PyDetectedSdk) {
      VirtualFile sdkHome = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          return LocalFileSystem.getInstance().refreshAndFindFileByPath(item.getName());
        }
      });
      newSdk = SdkConfigurationUtil.setupSdk(myProjectSdksModel.getSdks(), sdkHome, PythonSdkType.getInstance(), true, null, null);
      if (newSdk != null) {
        myProjectSdksModel.addSdk(newSdk);
        updateSdkList(false);
      }
      PySdkService.getInstance().solidifySdk(item);
    }
    myProjectSdksModel.apply();
    newSdk = newSdk == null ? null : myProjectSdksModel.findSdk(newSdk.getName());
    mySdkCombo.getModel().setSelectedItem(newSdk);

    final Sdk prevSdk = getSdk();
    setSdk(newSdk);

    // update string literals if different LanguageLevel was selected
    if (prevSdk != null && newSdk != null) {
      final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(newSdk);
      final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(prevSdk);
      if (flavor1 != null && flavor2 != null) {
        final LanguageLevel languageLevel1 = flavor1.getLanguageLevel(newSdk);
        final LanguageLevel languageLevel2 = flavor2.getLanguageLevel(prevSdk);
        if ((languageLevel1.isPy3K() && languageLevel2.isPy3K()) ||
            (!languageLevel1.isPy3K()) && !languageLevel2.isPy3K()) {
          return;
        }
      }
    }
    rehighlightStrings(myProject);
  }

  private void setSdk(final Sdk item) {
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        rootManager.setProjectSdk(item);
      }
    });
    if (myModule != null) {
      ModuleRootModificationUtil.setModuleSdk(myModule, item);
    }
  }

  public static void rehighlightStrings(final @NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {

        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
          if (editor instanceof EditorEx && editor.getProject() == project) {
            final VirtualFile vFile = ((EditorEx)editor).getVirtualFile();
            if (vFile != null) {
              final EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vFile);
              ((EditorEx)editor).setHighlighter(highlighter);
            }
          }
        }
      }
    });
  }

  @Override
  public void reset() {
    resetSdkList();
  }

  private void resetSdkList() {
    updateSdkList(false);

    final Sdk sdk = getSdk();
    mySdkCombo.getModel().setSelectedItem(sdk == null ? null : myProjectSdksModel.findSdk(sdk.getName()));
    myPackagesPanel.updatePackages(sdk != null ? new PyPackageManagementService(myProject, sdk) : null);
    myPackagesPanel.updateNotifications(sdk);

  }

  private void updateSdkList(boolean preserveSelection) {
    final List<Sdk> sdkList = myInterpreterList.getAllPythonSdks(myProject);
    Sdk selection = preserveSelection ? (Sdk)mySdkCombo.getSelectedItem() : null;
    if (!sdkList.contains(selection)) {
      selection = null;
    }
    VirtualEnvProjectFilter.removeNotMatching(myProject, sdkList);
    // if the selection is a non-matching virtualenv, show it anyway
    if (selection != null && !sdkList.contains(selection)) {
      sdkList.add(0, selection);
    }
    List<Object> items = new ArrayList<Object>();
    items.add(null);

    boolean remoteSeparator = true;
    boolean separator = true;
    boolean detectedSeparator = true;
    for (Sdk sdk : sdkList) {
      if (!PythonSdkType.isVirtualEnv(sdk) && !PythonSdkType.isRemote(sdk) && !(sdk instanceof PyDetectedSdk) && separator) {
        items.add(PySdkListCellRenderer.SEPARATOR);
        separator = false;
      }
      if (PythonSdkType.isRemote(sdk) && remoteSeparator) {
        items.add(PySdkListCellRenderer.SEPARATOR);
        remoteSeparator = false;
      }
      if (sdk instanceof PyDetectedSdk && detectedSeparator) {
        items.add(PySdkListCellRenderer.SEPARATOR);
        detectedSeparator = false;
      }
      items.add(sdk);
    }

    items.add(PySdkListCellRenderer.SEPARATOR);
    items.add(SHOW_ALL);

    mySdkCombo.setRenderer(new PySdkListCellRenderer(false));
    //noinspection unchecked
    mySdkCombo.setModel(new CollectionComboBoxModel(items, selection));
  }

  @Override
  public void disposeUIResources() {
    myProjectSdksModel.removeListener(mySdkModelListener);
    myInterpreterList.disposeModel();
  }

  private static class MySdkModelListener implements SdkModel.Listener {
    private final PyActiveSdkConfigurable myConfigurable;

    public MySdkModelListener(PyActiveSdkConfigurable configurable) {
      myConfigurable = configurable;
    }

    @Override
    public void sdkAdded(Sdk sdk) {
      myConfigurable.updateSdkList(true);
    }

    @Override
    public void beforeSdkRemove(Sdk sdk) {
      myConfigurable.updateSdkList(true);
    }

    @Override
    public void sdkChanged(Sdk sdk, String previousName) {
      myConfigurable.updateSdkList(true);
    }

    @Override
    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
    }
  }
}
