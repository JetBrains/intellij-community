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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.BundleNameEvaluator;
import com.intellij.lang.properties.PropertiesFileProcessor;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ResourceBundleNameReferenceEditor extends ReferenceEditorWithBrowseButton {
  public static final Key<Boolean> CLASS_NAME_REFERENCE_FRAGMENT = Key.create("CLASS_NAME_REFERENCE_FRAGMENT");
  private final Project myProject;
  private ResourceBundle mySelectedBundle;
  private String myChooserTitle;

  public ResourceBundleNameReferenceEditor(@NotNull final Project project, @Nullable final ResourceBundle selectedBundle) {
    this(project, selectedBundle, null);
  }

  public ResourceBundleNameReferenceEditor(@NotNull final Project project, @Nullable final ResourceBundle selectedBundle,
                                  @Nullable final GlobalSearchScope resolveScope) {
    super(null, project, new Function<String,Document>() {
      public Document fun(final String s) {
        //TODO really?
        return new DocumentImpl(s);
      }
    }, selectedBundle != null ? selectedBundle.getBaseName() : "");

    myProject = project;
    myChooserTitle = "Choose Class";
    addActionListener(new ChooseClassAction());
  }

  private class ChooseClassAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
      System.out.println("action performed ");
      final Set<ResourceBundle> bundles = new HashSet<ResourceBundle>();
      //TODO process only project editable scope
      PropertiesReferenceManager.getInstance(myProject).processPropertiesFiles(
        GlobalSearchScope.projectScope(myProject),
        new PropertiesFileProcessor() {
        @Override
        public boolean process(String baseName, PropertiesFile propertiesFile) {
          bundles.add(propertiesFile.getResourceBundle());
          return true;
        }
      },
        BundleNameEvaluator.NULL);
      final List<ResourceBundle> sortedBundles = ContainerUtil.sorted(ContainerUtil.filter(bundles, new Condition<ResourceBundle>() {
        @Override
        public boolean value(ResourceBundle resourceBundle) {
          return resourceBundle.getBaseDirectory() != null;
        }
      }), new Comparator<ResourceBundle>() {
        @Override
        public int compare(ResourceBundle o1, ResourceBundle o2) {
          return Comparing.compare(o1.getBaseName(), o2.getBaseName());
        }
      });

      final BaseListPopupStep<ResourceBundle> step =
        new BaseListPopupStep<ResourceBundle>("TODO", sortedBundles) {
          @Override
          public PopupStep onChosen(final ResourceBundle selectedBundle, final boolean finalChoice) {
            if (selectedBundle != null && finalChoice) {
              setText(selectedBundle.getBaseName());
              mySelectedBundle = selectedBundle;
            }
            return FINAL_CHOICE;
          }

          @NotNull
          @Override
          public String getTextFor(final ResourceBundle resourceBundle) {
            return resourceBundle.getBaseName();
          }

          @Override
          public Icon getIconFor(final ResourceBundle resourceBundle) {
            return AllIcons.Nodes.ResourceBundle;
          }
        };

      final ListPopupImpl popup = new ListPopupImpl(step);
      popup.showUnderneathOf(ResourceBundleNameReferenceEditor.this);

    }
  }
}