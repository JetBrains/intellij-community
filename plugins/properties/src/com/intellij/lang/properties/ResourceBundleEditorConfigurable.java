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
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.IgnoredPropertiesFilesSuffixesManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Dmitry Batkovich
 */
public class ResourceBundleEditorConfigurable extends BaseConfigurable {
  private final JPanel myPanel;
  private final CollectionListModel<String> mySuffixesModel;
  private final IgnoredPropertiesFilesSuffixesManager mySuffixesManager;

  public ResourceBundleEditorConfigurable(@NotNull Project project) {
    mySuffixesManager = IgnoredPropertiesFilesSuffixesManager.getInstance(project);
    final JBList list = new JBList();
    final List<String> suffixes = new ArrayList<String>(mySuffixesManager.getIgnoredSuffixes());
    mySuffixesModel = new CollectionListModel<String>(suffixes);
    mySuffixesModel.sort(String.CASE_INSENSITIVE_ORDER);
    list.setModel(mySuffixesModel);
    myPanel = ToolbarDecorator.createDecorator(list).disableUpDownActions().setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final String result = Messages.showInputDialog(CommonDataKeys.PROJECT.getData(button.getDataContext()),
                                                       "Suffixes to ignore (use comma to separate suffixes):",
                                                       "Add Ignored Suffixes", null);
        if (result != null) {
          final List<String> suffixes = StringUtil.split(result, ",");
          for (String suffix : suffixes) {
            if (mySuffixesModel.getElementIndex(suffix) == -1) {
              mySuffixesModel.add(suffix);
            }
          }
          updateModifiedStatus();
          mySuffixesModel.sort(String.CASE_INSENSITIVE_ORDER);
        }
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        for (Object toDelete : list.getSelectedValues()) {
          mySuffixesModel.remove((String)toDelete);
        }
        updateModifiedStatus();
      }
    }).createPanel();
    list.setCellRenderer(new ColoredListCellRenderer<String>() {
      @Override
      protected void customizeCellRenderer(JList list, String suffix, int index, boolean selected, boolean hasFocus) {
        append(suffix);
        final Locale locale = PropertiesUtil.getLocale("_" + suffix + ".properties");
        if (locale != PropertiesUtil.DEFAULT_LOCALE && PropertiesUtil.hasDefaultLanguage(locale)) {
          append(" ");
          append(PropertiesUtil.getPresentableLocale(locale), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
    });
    myPanel.setBorder(IdeBorderFactory.createTitledBorder("Ignored properties file suffixes:"));
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Resource Bundle Editor";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    mySuffixesManager.setSuffixes(mySuffixesModel.getItems());
    setModified(false);
  }

  @Override
  public void reset() {
    mySuffixesModel.removeAll();
    for (String suffix : mySuffixesManager.getIgnoredSuffixes()) {
      mySuffixesModel.add(suffix);
    }
    mySuffixesModel.sort(String.CASE_INSENSITIVE_ORDER);
    setModified(false);
  }

  @Override
  public void disposeUIResources() {
  }

  private void updateModifiedStatus() {
    setModified(!ContainerUtil.newHashSet(mySuffixesModel.getItems()).equals(mySuffixesManager.getIgnoredSuffixes()));
  }

  public static class Provider extends ConfigurableProvider {
    private final Project myProject;

    public Provider(Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public Configurable createConfigurable() {
      return new ResourceBundleEditorConfigurable(myProject);
    }
  }
}
