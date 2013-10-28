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
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public class PyActiveSdkConfigurable implements UnnamedConfigurable {
  private JPanel myPanel;
  private final Project myProject;
  @Nullable private final Module myModule;
  private JComboBox mySdkCombo;
  private JPanel myConfigureInterpretersPanel;
  private PyConfigurableInterpreterList myInterpreterList;
  private ProjectSdksModel myProjectSdksModel;
  private MyListener myListener;

  public PyActiveSdkConfigurable(Project project) {
    myModule = null;
    myProject = project;
    init();
  }

  public PyActiveSdkConfigurable(@NotNull Module module) {
    myModule = module;
    myProject = module.getProject();
    init();
  }

  private void init() {
    mySdkCombo.setRenderer(new SdkListCellRenderer("<None>"));

    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
    myListener = new MyListener(this);
    myProjectSdksModel.addListener(myListener);
    myConfigureInterpretersPanel.add(new PyConfigureInterpretersLinkPanel(myPanel), BorderLayout.CENTER);
    myInterpreterList.setSdkCombo(mySdkCombo);
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    final Sdk sdk = getSdk();
    return sdk != myProjectSdksModel.findSdk((Sdk)mySdkCombo.getSelectedItem());
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
    final Sdk selectedSdk = myProjectSdksModel.findSdk((Sdk)mySdkCombo.getSelectedItem());
    if (myModule == null) {
      final ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          rootManager.setProjectSdk(selectedSdk);
        }
      });
    }
    else {
      ModuleRootModificationUtil.setModuleSdk(myModule, selectedSdk);
    }
    final Sdk prevSdk = ProjectRootManager.getInstance(myProject).getProjectSdk();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectRootManager.getInstance(myProject).setProjectSdk(selectedSdk);
      }
    });

    myProjectSdksModel.setProjectSdk(selectedSdk);

    // update string literals if different LanguageLevel was selected
    if (prevSdk != null && selectedSdk != null) {
      final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(selectedSdk);
      final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(prevSdk);
      if (flavor1 != null && flavor2 != null) {
        final LanguageLevel languageLevel1 = flavor1.getLanguageLevel(selectedSdk);
        final LanguageLevel languageLevel2 = flavor2.getLanguageLevel(prevSdk);
        if ((languageLevel1.isPy3K() && languageLevel2.isPy3K()) ||
            (!languageLevel1.isPy3K()) && !languageLevel2.isPy3K()) {
          return;
        }
      }
    }
    rehighlightStrings(myProject);
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
    updateSdkList(false);

    final Sdk sdk = getSdk();
    mySdkCombo.setSelectedItem(myProjectSdksModel.getProjectSdks().get(sdk));
  }

  private void updateSdkList(boolean preserveSelection) {
    final List<Sdk> sdkList = myInterpreterList.getAllPythonSdks();
    Sdk selection = preserveSelection ? (Sdk)mySdkCombo.getSelectedItem() : null;
    if (!sdkList.contains(selection)) {
      selection = null;
    }
    VirtualEnvProjectFilter.removeNotMatching(myProject, sdkList);
    // if the selection is a non-matching virtualenv, show it anyway
    if (selection != null && !sdkList.contains(selection)) {
      sdkList.add(0, selection);
    }
    sdkList.add(0, null);
    mySdkCombo.setRenderer(new PySdkListCellRenderer());
    mySdkCombo.setModel(new CollectionComboBoxModel(sdkList, selection));
  }

  @Override
  public void disposeUIResources() {
    myProjectSdksModel.removeListener(myListener);
    myInterpreterList.disposeModel();
  }

  private static class MyListener implements SdkModel.Listener {
    private final PyActiveSdkConfigurable myConfigurable;

    public MyListener(PyActiveSdkConfigurable configurable) {
      myConfigurable = configurable;
    }

    @Override
    public void sdkAdded(Sdk sdk) {
      myConfigurable.reset();
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
