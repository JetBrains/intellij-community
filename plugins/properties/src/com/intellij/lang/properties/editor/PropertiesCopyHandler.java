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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.SyntheticFileSystemItem;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.refactoring.copy.CopyHandlerDelegateBase;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCopyHandler extends CopyHandlerDelegateBase {
  private final static Logger LOG = Logger.getInstance(PropertiesCopyHandler.class);

  @Override
  public boolean canCopy(PsiElement[] elements, boolean fromUpdate) {
    String propertyName = null;
    for (PsiElement element : elements) {
      final IProperty property = PropertiesImplUtil.getProperty(element);
      if (property == null) {
        return false;
      }
      else if (propertyName == null) {
        propertyName = property.getKey();
      }
      else if (!propertyName.equals(property.getKey())) {
        return false;
      }
    }
    return propertyName != null;
  }

  @Override
  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    final IProperty representative = PropertiesImplUtil.getProperty(elements[0]);
    final String key = representative.getKey();
    if (key == null) {
      return;
    }
    final ResourceBundle resourceBundle = representative.getPropertiesFile().getResourceBundle();
    final List<IProperty> properties = ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(),
                                                                (NullableFunction<PropertiesFile, IProperty>)propertiesFile -> propertiesFile.findPropertyByKey(key));
    final PropertiesCopyDialog dlg = new PropertiesCopyDialog(properties, resourceBundle);
    if (!properties.isEmpty() && dlg.showAndGet()) {
      final String propertyNewName = dlg.getCurrentPropertyName();
      final ResourceBundle destinationResourceBundle = dlg.getCurrentResourceBundle();
      copyPropertyToAnotherBundle(properties, propertyNewName, destinationResourceBundle);
    }
  }

  @Override
  public void doClone(PsiElement element) {
  }

  private static void copyPropertyToAnotherBundle(@NotNull Collection<IProperty> properties,
                                                  @NotNull final String newName,
                                                  @NotNull ResourceBundle targetResourceBundle) {
    final Map<IProperty, PropertiesFile> propertiesFileMapping = new HashMap<>();
    for (IProperty property : properties) {
      final PropertiesFile containingFile = property.getPropertiesFile();
      final PropertiesFile matched = findWithMatchedSuffix(containingFile, targetResourceBundle);
      if (matched != null) {
        propertiesFileMapping.put(property, matched);
      }
    }

    final Project project = targetResourceBundle.getProject();
    if (properties.size() != propertiesFileMapping.size() &&
        Messages.NO == Messages.showYesNoDialog(project,
                                                 "Source and target resource bundles properties files are not matched correctly. Copy properties anyway?",
                                                 "Resource Bundles Are not Matched", null)) {
      return;
    }

    if (!propertiesFileMapping.isEmpty()) {
      WriteCommandAction.runWriteCommandAction(project, () -> {
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(ContainerUtil.map(propertiesFileMapping.values(),
                                                                                                (Function<PropertiesFile, PsiElement>)file -> file.getContainingFile()))) return;
        for (Map.Entry<IProperty, PropertiesFile> entry : propertiesFileMapping.entrySet()) {
          final String value = entry.getKey().getValue();
          final PropertiesFile target = entry.getValue();
          target.addProperty(newName, value);
        }
      });

      final IProperty representativeFromSourceBundle = ContainerUtil.getFirstItem(properties);
      LOG.assertTrue(representativeFromSourceBundle != null);
      final ResourceBundle sourceResourceBundle = representativeFromSourceBundle.getPropertiesFile().getResourceBundle();
      if (sourceResourceBundle.equals(targetResourceBundle)) {
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
          @Override
          public void consume(DataContext context) {
            final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
            if (fileEditor instanceof ResourceBundleEditor) {
              final ResourceBundleEditor resourceBundleEditor = (ResourceBundleEditor)fileEditor;
              resourceBundleEditor.updateTreeRoot();
              resourceBundleEditor.selectProperty(newName);
            }
          }
        });
      } else {
        for (FileEditor editor : FileEditorManager.getInstance(project).openFile(new ResourceBundleAsVirtualFile(targetResourceBundle), true)) {
          ((ResourceBundleEditor) editor).updateTreeRoot();
          ((ResourceBundleEditor) editor).selectProperty(newName);
        }
      }
    }
  }

  @Nullable
  private static PropertiesFile findWithMatchedSuffix(@NotNull PropertiesFile searchFile, @NotNull ResourceBundle resourceBundle) {
    final String targetSuffix = getPropertiesFileSuffix(searchFile, searchFile.getResourceBundle().getBaseName());

    final String baseName = resourceBundle.getBaseName();
    for (PropertiesFile propertiesFile : resourceBundle.getPropertiesFiles()) {
      if (getPropertiesFileSuffix(propertiesFile, baseName).equals(targetSuffix)) {
        return propertiesFile;
      }
    }
    return null;
  }

  @NotNull
  private static String getPropertiesFileSuffix(PropertiesFile searchFile, String baseName) {
    String suffix = FileUtil.getNameWithoutExtension(searchFile.getContainingFile().getName());
    suffix = StringUtil.trimStart(suffix, baseName);
    return suffix;
  }

  private static class PropertiesCopyDialog extends DialogWrapper {
    @NotNull private final List<IProperty> myProperties;
    @NotNull private ResourceBundle myCurrentResourceBundle;
    private String myCurrentPropertyName;
    @NotNull private final Project myProject;
    private JBTextField myPropertyNameTextField;

    protected PropertiesCopyDialog(@NotNull List<IProperty> properties,
                                   @NotNull ResourceBundle currentResourceBundle) {

      super(currentResourceBundle.getProject());
      myProperties = properties;
      myCurrentResourceBundle = currentResourceBundle;
      myCurrentPropertyName = ContainerUtil.getFirstItem(properties).getName();
      myProject = currentResourceBundle.getProject();
      init();
      initValidation();
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
      if (StringUtil.isEmpty(myCurrentPropertyName)) {
        return new ValidationInfo("Property name must be not empty");
      }
      return PropertiesUtil.containsProperty(myCurrentResourceBundle, myCurrentPropertyName)
             ? new ValidationInfo(String.format("Property with name \'%s\' already exists", myCurrentPropertyName))
             : null;
    }

    public String getCurrentPropertyName() {
      return myCurrentPropertyName;
    }

    @NotNull
    public ResourceBundle getCurrentResourceBundle() {
      return myCurrentResourceBundle;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JLabel informationalLabel = new JLabel();
      informationalLabel.setText("Copy property " + ContainerUtil.getFirstItem(myProperties).getName());
      informationalLabel.setFont(informationalLabel.getFont().deriveFont(Font.BOLD));

      final Collection<PropertiesFile> propertiesFiles = new ArrayList<>();

      GlobalSearchScope searchScope = ProjectScope.getContentScope(myProject);
      PropertiesReferenceManager
        .getInstance(myProject)
        .processPropertiesFiles(searchScope,
                                new PropertiesFileProcessor() {
                                  @Override
                                  public boolean process(String baseName, PropertiesFile propertiesFile) {
                                    propertiesFiles.add(propertiesFile);
                                    return true;
                                  }
                                }, BundleNameEvaluator.BASE_NAME);

      final List<PsiFileSystemItem> resourceBundlesAsFileSystemItems = propertiesFiles
        .stream()
        .map(PropertiesFile::getResourceBundle)
        .distinct()
        .filter(b -> b.getBaseDirectory() != null)
        .sorted((o1, o2) -> Comparing.compare(o1.getBaseName(), o2.getBaseName()))
        .map(ResourceBundleAsFileSystemItem::new)
        .collect(Collectors.toList());

      final ComboBox<PsiFileSystemItem> resourceBundleComboBox =
        new ComboBox<>(resourceBundlesAsFileSystemItems.toArray(new PsiFileSystemItem[resourceBundlesAsFileSystemItems.size()]));
      new ComboboxSpeedSearch(resourceBundleComboBox) {
        @Override
        protected String getElementText(Object element) {
          return ((PsiFileSystemItem) element).getName();
        }
      };

      //noinspection GtkPreferredJComboBoxRenderer
      resourceBundleComboBox.setRenderer(new GotoFileCellRenderer(500) {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          UIUtil.setBackgroundRecursively(component, isSelected ? UIUtil.getListSelectionBackground() : resourceBundleComboBox.getBackground());
          return component;
        }
      });
      resourceBundleComboBox.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(@NotNull ItemEvent e) {
          myCurrentResourceBundle = ((ResourceBundleAsFileSystemItem)e.getItem()).getResourceBundle();
        }
      });
      resourceBundleComboBox.setSelectedItem(new ResourceBundleAsFileSystemItem(myCurrentResourceBundle));

      myPropertyNameTextField = new JBTextField(ContainerUtil.getFirstItem(myProperties).getKey());
      myPropertyNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          myCurrentPropertyName = myPropertyNameTextField.getText();
        }
      });
      return FormBuilder
        .createFormBuilder()
        .addComponent(informationalLabel)
        .addLabeledComponent("&New name:", myPropertyNameTextField, UIUtil.LARGE_VGAP)
        .addLabeledComponent("&Destination resource bundle:", resourceBundleComboBox)
        .getPanel();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myPropertyNameTextField;
    }
  }

  private static class ResourceBundleAsFileSystemItem extends SyntheticFileSystemItem {
    private final ResourceBundle myResourceBundle;

    public ResourceBundleAsFileSystemItem(@NotNull ResourceBundle resourceBundle) {
      super(resourceBundle.getProject());
      myResourceBundle = resourceBundle;
    }

    public ResourceBundle getResourceBundle() {
      return myResourceBundle;
    }

    @NotNull
    @Override
    public String getName() {
      return myResourceBundle.getBaseName();
    }

    @Nullable
    @Override
    public PsiFileSystemItem getParent() {
      return PsiManager.getInstance(getProject()).findDirectory(myResourceBundle.getBaseDirectory());
    }

    @Override
    public VirtualFile getVirtualFile() {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    }

    @Override
    public boolean processChildren(PsiElementProcessor<PsiFileSystemItem> processor) {
      for (PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
        if (!propertiesFile.getContainingFile().processChildren(processor)) {
          return false;
        }
      }
      return true;
    }

    @Nullable
    @Override
    public Icon getIcon(int flags) {
      return AllIcons.Nodes.ResourceBundle;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ResourceBundleAsFileSystemItem item = (ResourceBundleAsFileSystemItem)o;
      return myResourceBundle.equals(item.myResourceBundle);
    }

    @Override
    public int hashCode() {
      return myResourceBundle.hashCode();
    }
  }

}
