/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class IgnoreIncompletePropertyPropertiesFilesAction extends AnAction {
  private final static Logger LOG = Logger.getInstance(IgnoreIncompletePropertyPropertiesFilesAction.class);

  public IgnoreIncompletePropertyPropertiesFilesAction() {
    super("Ignore Properties Files Without Translation");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
    LOG.assertTrue(resourceBundleEditor != null);
    final Project project = getEventProject(e);
    LOG.assertTrue(project != null);

    final Set<String> properties = new HashSet<String>();
    processSelectedIncompleteProperties(new Processor<IProperty>() {
      @Override
      public boolean process(IProperty property) {
        properties.add(property.getKey());
        return true;
      }
    }, resourceBundleEditor, project);

    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(project);
    final List<PropertiesFile> allFilesWithoutTranslation =
      suffixesManager.getPropertiesFilesWithoutTranslation(resourceBundleEditor.getResourceBundle(), properties);
    if (allFilesWithoutTranslation.isEmpty()) {
      return;
    }
    Collections.sort(allFilesWithoutTranslation, new Comparator<PropertiesFile>() {
      @Override
      public int compare(PropertiesFile p1, PropertiesFile p2) {
        return p1.getName().compareTo(p2.getName());
      }
    });

    final List<PropertiesFile> suffixRepresentatives =
      new IgnoredSuffixesDialog(allFilesWithoutTranslation, project).showAndGetSuffixesRepresentatives();
    if (suffixRepresentatives == null) {
      return;
    }
    final List<String> suffixesToIgnore = ContainerUtil.map(suffixRepresentatives, new NotNullFunction<PropertiesFile, String>() {
      @NotNull
      @Override
      public String fun(PropertiesFile propertiesFile) {
        return PropertiesUtil.getSuffix(propertiesFile);
      }
    });
    if (!suffixesToIgnore.isEmpty()) {
      suffixesManager.addSuffixes(suffixesToIgnore);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          resourceBundleEditor.queueUpdateTree();
        }
      });
    }
  }

  private static class IgnoredSuffixesDialog extends DialogWrapper {
    @NotNull private final List<PropertiesFile> myPropertiesFiles;
    private CollectionListModel<PropertiesFile> myModel;

    protected IgnoredSuffixesDialog(@NotNull List<PropertiesFile> propertiesFiles, @NotNull Project project) {
      super(project);
      myPropertiesFiles = propertiesFiles;
      setTitle("Suffixes to Ignore:");
      init();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      final JBList list = new JBList();
      myModel = new CollectionListModel<PropertiesFile>(myPropertiesFiles);
      list.setModel(myModel);
      list.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          label.setText(PropertiesUtil.getSuffix((PropertiesFile)value));
          return label;
        }
      });
      return ToolbarDecorator.createDecorator(list).disableUpDownActions().disableAddAction().createPanel();
    }

    @Nullable
    public List<PropertiesFile> showAndGetSuffixesRepresentatives() {
      return showAndGet() ? myModel.getItems() : null;
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(e.getDataContext());
    if (fileEditor instanceof ResourceBundleEditor) {
      ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)fileEditor;
      final Project project = getEventProject(e);
      if (project != null) {
        if (!processSelectedIncompleteProperties(new Processor<IProperty>() {
          @Override
          public boolean process(IProperty property) {
            return false;
          }
        }, resourceBundleEditor, project)) {
          e.getPresentation().setEnabledAndVisible(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabledAndVisible(false);
  }

  private static boolean processSelectedIncompleteProperties(final @NotNull Processor<IProperty> processor,
                                                             final @NotNull ResourceBundleEditor resourceBundleEditor,
                                                             final @NotNull Project project) {
    final IgnoredPropertiesFilesSuffixesManager suffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(project);
    for (ResourceBundleEditorViewElement element : resourceBundleEditor.getSelectedElements()) {
      final IProperty[] properties = element.getProperties();
      if (properties != null) {
        for (IProperty property : properties) {
          if (!suffixesManager.isPropertyComplete(resourceBundleEditor.getResourceBundle(), property.getKey()) && !processor.process(property)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
