// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.*;

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
    if (representative == null) return;
    final String key = representative.getKey();
    if (key == null) {
      return;
    }
    final ResourceBundle resourceBundle = representative.getPropertiesFile().getResourceBundle();
    final List<IProperty> properties = ContainerUtil.mapNotNull(resourceBundle.getPropertiesFiles(),
                                                                propertiesFile -> propertiesFile.findPropertyByKey(key));
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

  private void copyPropertyToAnotherBundle(@NotNull Collection<? extends IProperty> properties,
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
                                                PropertiesBundle.message("copy.resource.bundles.are.not.matched.message"),
                                                PropertiesBundle.message("copy.resource.bundles.are.not.matched.title"), null)) {
      return;
    }

    if (!propertiesFileMapping.isEmpty()) {
      WriteCommandAction.runWriteCommandAction(project, () -> {
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(ContainerUtil.map(propertiesFileMapping.values(),
                                                                                                PropertiesFile::getContainingFile))) return;
        for (Map.Entry<IProperty, PropertiesFile> entry : propertiesFileMapping.entrySet()) {
          final String value = entry.getKey().getValue();
          final PropertiesFile target = entry.getValue();
          target.addProperty(newName, value);
        }
      });

      final IProperty representativeFromSourceBundle = ContainerUtil.getFirstItem(properties);
      LOG.assertTrue(representativeFromSourceBundle != null);
      final ResourceBundle sourceResourceBundle = representativeFromSourceBundle.getPropertiesFile().getResourceBundle();
      updateBundleEditors(newName, targetResourceBundle, sourceResourceBundle, project);
    }
  }

  protected void updateBundleEditors(@NotNull String newName,
                                     @NotNull ResourceBundle targetResourceBundle,
                                     @NotNull ResourceBundle sourceResourceBundle,
                                     @NotNull Project project) { }

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
    String suffix = FileUtilRt.getNameWithoutExtension(searchFile.getContainingFile().getName());
    suffix = StringUtil.trimStart(suffix, baseName);
    return suffix;
  }

  private static class PropertiesCopyDialog extends DialogWrapper {
    @NotNull private final List<? extends IProperty> myProperties;
    @NotNull private ResourceBundle myCurrentResourceBundle;
    private String myCurrentPropertyName;
    @NotNull private final Project myProject;
    private JBTextField myPropertyNameTextField;

    protected PropertiesCopyDialog(@NotNull List<? extends IProperty> properties,
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
        return new ValidationInfo(PropertiesBundle.message("copy.property.name.must.be.not.empty.error"));
      }
      return PropertiesUtil.containsProperty(myCurrentResourceBundle, myCurrentPropertyName)
             ? new ValidationInfo(PropertiesBundle.message("copy.property.with.name.0.already.exists.conflict", myCurrentPropertyName))
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
      informationalLabel.setText(PropertiesBundle.message("copy.property.0.label", ContainerUtil.getFirstItem(myProperties).getName()));
      informationalLabel.setFont(informationalLabel.getFont().deriveFont(Font.BOLD));

      final Collection<PropertiesFile> propertiesFiles = new ArrayList<>();

      GlobalSearchScope searchScope = ProjectScope.getContentScope(myProject);
      PropertiesReferenceManager
        .getInstance(myProject)
        .processPropertiesFiles(searchScope,
                                (baseName, propertiesFile) -> {
                                  propertiesFiles.add(propertiesFile);
                                  return true;
                                }, BundleNameEvaluator.BASE_NAME);

      PsiFileSystemItem[] resourceBundlesAsFileSystemItems = propertiesFiles
        .stream()
        .map(PropertiesFile::getResourceBundle)
        .distinct()
        .filter(b -> b.getBaseDirectory() != null)
        .sorted(Comparator.comparing(ResourceBundle::getBaseName))
        .map(ResourceBundleAsFileSystemItem::new)
        .toArray(PsiFileSystemItem[]::new);
      final ComboBox<PsiFileSystemItem> resourceBundleComboBox = new ComboBox<>(resourceBundlesAsFileSystemItems);
      new ComboboxSpeedSearch(resourceBundleComboBox) {
        @Override
        protected String getElementText(Object element) {
          return ((PsiFileSystemItem) element).getName();
        }
      };

      resourceBundleComboBox.setRenderer(new GotoFileCellRenderer(500) {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          UIUtil.setBackgroundRecursively(component, isSelected ? UIUtil.getListSelectionBackground(true) : resourceBundleComboBox.getBackground());
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
        protected void textChanged(@NotNull DocumentEvent e) {
          myCurrentPropertyName = myPropertyNameTextField.getText();
        }
      });
      return FormBuilder
        .createFormBuilder()
        .addComponent(informationalLabel)
        .addLabeledComponent(PropertiesBundle.message("copy.destination.new.name.label"), myPropertyNameTextField, UIUtil.LARGE_VGAP)
        .addLabeledComponent(PropertiesBundle.message("copy.destination.resource.bundle.label"), resourceBundleComboBox)
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

    ResourceBundleAsFileSystemItem(@NotNull ResourceBundle resourceBundle) {
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
      VirtualFile dir = myResourceBundle.getBaseDirectory();
      return dir == null ? null : PsiManager.getInstance(getProject()).findDirectory(dir);
    }

    @Override
    public VirtualFile getVirtualFile() {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    }

    @Override
    public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
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
