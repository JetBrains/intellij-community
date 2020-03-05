// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.codeInspection.i18n.batch.I18nizeMultipleStringsDialog;
import com.intellij.codeInspection.i18n.batch.I18nizedPropertyData;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.references.I18nizeQuickFixDialog;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.labels.LinkLabel;
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
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import icons.UIDesignerIcons;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class I18nizeFormBatchFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
  private static final Logger LOG = Logger.getInstance(I18nizeFormBatchFix.class);
  private static final List<DefaultPrefixSuggestion> PREFIX_SUGGESTIONS = Arrays.asList(
    new DefaultPrefixSuggestion(LinkLabel.class, "text", "link"),
    new DefaultPrefixSuggestion(JLabel.class, "text", "label"),
    new DefaultPrefixSuggestion(JLabel.class, "toolTipText", "tooltip"),
    new DefaultPrefixSuggestion(JRadioButton.class, "text", "radio.button"),
    new DefaultPrefixSuggestion(JCheckBox.class, "text", "checkbox"),
    new DefaultPrefixSuggestion(JButton.class, "text", "button"),
    new DefaultPrefixSuggestion(TitledSeparator.class, "text", "separator.title")
  );

  @Override
  public void applyFix(@NotNull Project project,
                       CommonProblemDescriptor @NotNull [] descriptors,
                       @NotNull List<PsiElement> psiElementsToIgnore,
                       @Nullable Runnable refreshViews) {
    List<I18nizedPropertyData<HardcodedStringInFormData>> dataList = new ArrayList<>();
    HashSet<PsiFile> contextFiles = new HashSet<>();
    Map<VirtualFile, RadRootContainer> containerMap = new HashMap<>();
    UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    Map<String, List<I18nizedPropertyData<HardcodedStringInFormData>>> myDuplicates = new HashMap<>();
    for (CommonProblemDescriptor descriptor : descriptors) {
      FormElementProblemDescriptor formElementProblemDescriptor = (FormElementProblemDescriptor)descriptor;
      PsiFile containingFile = formElementProblemDescriptor.getPsiElement().getContainingFile();
      contextFiles.add(containingFile);
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
      String keyPrefix = suggestPropertyKeyPrefix(component, propertyName);
      String defaultKey = keyPrefix != null ? keyPrefix + "." + I18nizeQuickFixDialog.generateDefaultPropertyKey(value) : null;
      String key = uniqueNameGenerator.generateUniqueName(I18nizeQuickFixDialog.suggestUniquePropertyKey(value, defaultKey, null));
      I18nizedPropertyData<HardcodedStringInFormData> data = new I18nizedPropertyData<HardcodedStringInFormData>(key, value, new HardcodedStringInFormData(component, propertyName, containingFile));
      if (myDuplicates.containsKey(value)) {
        myDuplicates.computeIfAbsent(value, k -> new ArrayList<>(1)).add(data);
      }
      else {
        dataList.add(data);
        myDuplicates.put(value, null);
      }
    }

    I18nizeMultipleStringsDialog<HardcodedStringInFormData> dialog = new I18nizeMultipleStringsDialog<HardcodedStringInFormData>(project, dataList, contextFiles, this::createUsageInfo,
                                                                                                                                 UIDesignerIcons.InspectionSuppression);
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
        for (I18nizedPropertyData<HardcodedStringInFormData> bean : dataList) {
          StringDescriptor valueDescriptor;
          if (!bean.isMarkAsNonNls()) {
            JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER.createProperty(project,
                                                                          Collections.singletonList(propertiesFile),
                                                                          bean.getKey(),
                                                                          bean.getValue(),
                                                                          PsiExpression.EMPTY_ARRAY);
            valueDescriptor = new StringDescriptor(bundleName, bean.getKey());
          }
          else {
            valueDescriptor = StringDescriptor.create(bean.getValue());
            valueDescriptor.setNoI18n(true);
          }

          setPropertyValue(bean.getContextData().getComponent(), bean.getContextData().getPropertyName(), valueDescriptor);
          List<I18nizedPropertyData<HardcodedStringInFormData>> duplicates = myDuplicates.get(bean.getValue());
          if (duplicates != null) {
            for (I18nizedPropertyData<HardcodedStringInFormData> duplicateBean : duplicates) {
              setPropertyValue(duplicateBean.getContextData().getComponent(), duplicateBean.getContextData().getPropertyName(),
                               valueDescriptor);
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

  @Nullable
  private String suggestPropertyKeyPrefix(@NotNull RadComponent component, @NotNull String propertyName) {
    Class componentClass = component.getComponentClass();
    for (DefaultPrefixSuggestion suggestion : PREFIX_SUGGESTIONS) {
      if (suggestion.getPropertyName().equals(propertyName) && suggestion.getComponentClass().isAssignableFrom(componentClass)) {
        return suggestion.getDefaultPrefix();
      }
    }
    if (BorderProperty.NAME.equals(propertyName)) {
      return "border.title";
    }
    return null;
  }

  private List<UsageInfo> createUsageInfo(HardcodedStringInFormData data) {
    RadComponent component = data.getComponent();
    PsiFile file = data.getContainingFile();
    TextRange range = getComponentRange(component, file);
    UsageInfo usageInfo = range != null ? new UsageInfo(file, range.getStartOffset(), range.getEndOffset()) : new UsageInfo(file);
    return Collections.singletonList(usageInfo);
  }

  private TextRange getComponentRange(RadComponent component, PsiFile file) {
    CharSequence contents = file.getViewProvider().getContents();
    int componentId = StringUtil.indexOf(contents, "id=\"" + component.getId() + "\"");
    if (componentId == -1) return null;

    PsiFileFactory fileFactory = PsiFileFactory.getInstance(file.getProject());
    XmlFile xmlFile = (XmlFile)fileFactory.createFileFromText("form.xml", XmlFileType.INSTANCE, contents);
    XmlTag componentTag = PsiTreeUtil.getParentOfType(xmlFile.findElementAt(componentId), XmlTag.class);
    return componentTag != null ? componentTag.getTextRange() : null;
  }

  private static void setPropertyValue(RadComponent component, String propertyName, StringDescriptor stringDescriptor) {
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

  private static class HardcodedStringInFormData {
    private final RadComponent myComponent;
    private final String myPropertyName;
    private final PsiFile myContainingFile;

    private HardcodedStringInFormData(@NotNull RadComponent component,
                                      @NotNull String propertyName,
                                      @NotNull PsiFile containingFile) {
      myComponent = component;
      myPropertyName = propertyName;
      myContainingFile = containingFile;
    }

    private RadComponent getComponent() {
      return myComponent;
    }

    private String getPropertyName() {
      return myPropertyName;
    }

    private PsiFile getContainingFile() {
      return myContainingFile;
    }
  }

  private static class DefaultPrefixSuggestion {
    private final Class<?> myComponentClass;
    private final String myPropertyName;
    private final String myDefaultPrefix;

    private DefaultPrefixSuggestion(Class<?> componentClass, String propertyName, String defaultPrefix) {
      myComponentClass = componentClass;
      myPropertyName = propertyName;
      myDefaultPrefix = defaultPrefix;
    }

    private Class<?> getComponentClass() {
      return myComponentClass;
    }

    private String getPropertyName() {
      return myPropertyName;
    }

    private String getDefaultPrefix() {
      return myDefaultPrefix;
    }
  }
}
