// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.inspections.FormElementProblemDescriptor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import com.intellij.util.ui.ItemRemovable;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class I18nizeFormBatchFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
  private static final Logger LOG = Logger.getInstance(I18nizeFormBatchFix.class);

  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<ReplacementBean> beans = new ArrayList<>();
    HashSet<Module> contextModules = new HashSet<>();
    Map<VirtualFile, RadRootContainer> containerMap = new HashMap<>();
    UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    Map<String, List<ReplacementBean>> myDuplicates = new HashMap<>();
    for (CommonProblemDescriptor descriptor : descriptors) {
      FormElementProblemDescriptor formElementProblemDescriptor = (FormElementProblemDescriptor)descriptor;
      PsiFile containingFile = formElementProblemDescriptor.getPsiElement().getContainingFile();
      ContainerUtil.addIfNotNull(contextModules, ModuleUtilCore.findModuleForFile(containingFile));
      VirtualFile virtualFile = containingFile.getVirtualFile();

      final RadRootContainer rootContainer = containerMap.computeIfAbsent(virtualFile, f -> {
        try {
          final ClassLoader classLoader = LoaderFactory.getInstance(project).getLoader(virtualFile);
          LwRootContainer lwRootContainer = Utils.getRootContainer(containingFile.getText(), new CompiledClassPropertiesProvider(classLoader));
          Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);

          ModuleProvider moduleProvider = new ModuleProvider() {
            @Override
            public Module getModule() {
              return module;
            }

            @Override
            public Project getProject() {
              return project;
            }
          };

          return XmlReader.createRoot(moduleProvider, lwRootContainer, LoaderFactory.getInstance(project).getLoader(virtualFile), null);
        }
        catch (Exception e) {
          LOG.error(e);
          return null;
        }
      });
      if (rootContainer == null) continue;
      RadComponent component = (RadComponent)FormEditingUtil.findComponent(rootContainer, formElementProblemDescriptor.getComponentId());
      if (component == null) continue;
      String propertyName = formElementProblemDescriptor.getPropertyName();
      String value = getValue(component, propertyName);
      if (value == null) continue;
      ReplacementBean bean = new ReplacementBean(uniqueNameGenerator.generateUniqueName(I18nizeQuickFixDialog.suggestUniquePropertyKey(value, null, null)),
                                                 component,
                                                 propertyName,
                                                 value);
      if (myDuplicates.containsKey(value)) {
        myDuplicates.computeIfAbsent(value, k -> new ArrayList<>(1)).add(bean);
      }
      else {
        beans.add(bean);
        myDuplicates.put(value, null);
      }
    }

    I18NBatchDialog dialog = new I18NBatchDialog(project, beans, contextModules);
    if (dialog.showAndGet()) {
      PropertiesFile propertiesFile = dialog.getPropertiesFile();
      PsiManager manager = PsiManager.getInstance(project);
      Set<PsiFile> files = new HashSet<>();
      for (VirtualFile file : containerMap.keySet()) {
        ContainerUtil.addIfNotNull(files, manager.findFile(file));
      }
      if (files.isEmpty()) {
        return;
      }
      files.add(propertiesFile.getContainingFile());

      String bundleName = I18nizeFormQuickFix.getBundleName(project, propertiesFile);
      if (bundleName == null) {
        return;
      }
      WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
        for (ReplacementBean bean : beans) {
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                        Collections.singletonList(propertiesFile),
                                                                        bean.myKey,
                                                                        bean.myValue,
                                                                        PsiExpression.EMPTY_ARRAY);
          applyFix(bean.myComponent, bean.myPropertyName, bundleName, bean.myKey);
          List<ReplacementBean> duplicates = myDuplicates.get(bean.myValue);
          if (duplicates != null) {
            for (ReplacementBean duplicateBean : duplicates) {
              applyFix(duplicateBean.myComponent, duplicateBean.myPropertyName, bundleName, bean.myKey);
            }
          }
        }

        for (Map.Entry<VirtualFile, RadRootContainer> entry : containerMap.entrySet()) {
          try {
            final XmlWriter writer = new XmlWriter();
            entry.getValue().write(writer);
            FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(entry.getKey()), writer.getText());

            FileEditor[] editors = FileEditorManager.getInstance(project).getAllEditors(entry.getKey());
            for (FileEditor editor : editors) {
              if (editor instanceof UIFormEditor) {
                ((UIFormEditor)editor).getEditor().refresh();
              }
            }
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }, files.toArray(PsiFile.EMPTY_ARRAY));
    }
  }

  private static void applyFix(RadComponent component, String propertyName, String resourceBundle, String key) {
    StringDescriptor stringDescriptor = new StringDescriptor(resourceBundle, key);
    if (BorderProperty.NAME.equals(propertyName)) {
      ((RadContainer)component).setBorderTitle(stringDescriptor);
    }
    else if (propertyName.equals(ITabbedPane.TAB_TITLE_PROPERTY) || propertyName.equals(ITabbedPane.TAB_TOOLTIP_PROPERTY)) {
      try {
        new TabTitleStringDescriptorAccessor(component, propertyName).setStringDescriptorValue(stringDescriptor);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    else {
      Arrays.stream(component.getModifiedProperties())
        .filter(p -> propertyName.equals(p.getName()))
        .findFirst()
        .ifPresent(p -> {
        try {
          new FormPropertyStringDescriptorAccessor(component, (IntrospectedProperty)p).setStringDescriptorValue(stringDescriptor);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      });
    }
  }

  private static String getValue(IComponent component, String propertyName) {
    if (BorderProperty.NAME.equals(propertyName)) {
      return ((IContainer)component).getBorderTitle().getValue();
    }
    else if (propertyName.equals(ITabbedPane.TAB_TITLE_PROPERTY) || propertyName.equals(ITabbedPane.TAB_TOOLTIP_PROPERTY)) {
      return ((ITabbedPane)component.getParentContainer()).getTabProperty(component, propertyName).getValue();
    }
    for (IProperty property : component.getModifiedProperties()) {
      if (property.getName().equals(propertyName)) {
        return ((StringDescriptor)property.getPropertyValue(component)).getValue();
      }
    }
    return null;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JavaI18nBundle.message("inspection.i18n.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) { }

  public static Element findElementById(String id, Element rootElement) {
    if (id.equals(rootElement.getAttributeValue("id"))) {
      return rootElement;
    }
    for (Element child : rootElement.getChildren()) {
      Element elementById = findElementById(id, child);
      if (elementById != null) return elementById;
    }
    return null;
  }

  private static class ReplacementBean {
    private final RadComponent myComponent;
    private final String myPropertyName;
    private String myKey;
    private final String myValue;

    private ReplacementBean(String key,
                            RadComponent component,
                            String propertyName,
                            String value) {
      myComponent = component;
      myPropertyName = propertyName;
      myKey = key;
      myValue = value;
    }
  }

  private static class I18NBatchDialog extends DialogWrapper {
    private static final @NonNls String LAST_USED_PROPERTIES_FILE = "LAST_USED_PROPERTIES_FILE";

    @NotNull private final Project myProject;
    private final List<ReplacementBean> myBeans;
    private final Set<Module> myContextModules;
    private JComboBox<String> myPropertiesFile;

    protected I18NBatchDialog(@NotNull Project project,
                              List<ReplacementBean> beans,
                              Set<Module> contextModules) {
      super(project, true);
      myProject = project;
      myBeans = beans;
      myContextModules = contextModules;
      setTitle(PropertiesBundle.message("i18nize.dialog.title"));
      init();
    }

    @Override
    protected @Nullable String getDimensionServiceKey() {
      return "i18nFormInBatch";
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      List<String> files = I18nUtil.defaultSuggestPropertiesFiles(myProject, myContextModules);
      myPropertiesFile = new ComboBox<>(ArrayUtil.toStringArray(files));
      new ComboboxSpeedSearch(myPropertiesFile);
      LabeledComponent<JComboBox<String>> component = new LabeledComponent<>();
      component.setText(JavaI18nBundle.message("property.file"));
      component.setComponent(myPropertiesFile);
      myPropertiesFile.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          PropertiesFile propertiesFile = getPropertiesFile();
          if (propertiesFile != null) {
            for (ReplacementBean bean : myBeans) {
              bean.myKey = I18nizeQuickFixDialog.suggestUniquePropertyKey(bean.myValue, bean.myKey, propertiesFile);
            }
          }
        }
      });

      if (!files.isEmpty()) {
        myPropertiesFile.setSelectedItem(ObjectUtils.notNull(PropertiesComponent.getInstance(myProject).getValue(LAST_USED_PROPERTIES_FILE),
                                                             files.get(0)));
      }
      return component;
    }

    protected PropertiesFile getPropertiesFile() {
      Object selectedItem = myPropertiesFile.getSelectedItem();
      if (selectedItem == null) return null;
      String path = FileUtil.toSystemIndependentName((String)selectedItem);
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      return virtualFile != null
             ? PropertiesImplUtil.getPropertiesFile(PsiManager.getInstance(myProject).findFile(virtualFile))
             : null;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JBTable table = new JBTable(new  I18NBatchDialog.MyKeyValueModel());


      return ToolbarDecorator.createDecorator(table).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(table);
          table.repaint();
        }
      }).createPanel();
    }

    @Override
    protected void doOKAction() {
      PropertiesComponent.getInstance(myProject).setValue(LAST_USED_PROPERTIES_FILE, (String)myPropertiesFile.getSelectedItem());
      super.doOKAction();
    }

    private class MyKeyValueModel extends AbstractTableModel implements ItemRemovable {
      @Override
      public int getRowCount() {
        return myBeans.size();
      }

      @Override
      public String getColumnName(int column) {
        return column == 0 ? "Key" : "Value";
      }

      @Override
      public int getColumnCount() {
        return 2;
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        ReplacementBean bean = myBeans.get(rowIndex);
        return columnIndex == 0 ? bean.myKey : bean.myValue;
      }

      @Override
      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
          myBeans.get(rowIndex).myKey = (String)aValue;
        }
      }

      @Override
      public void removeRow(int idx) {
        myBeans.remove(idx);
      }
    }
  }

}
