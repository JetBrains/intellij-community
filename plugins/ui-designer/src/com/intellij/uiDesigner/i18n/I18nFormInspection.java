// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.inspections.EditorQuickFixProvider;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.inspections.StringDescriptorInspection;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.ITabbedPane;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class I18nFormInspection extends StringDescriptorInspection {
  public I18nFormInspection() {
    super("I18nForm");
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "HardCodedStringLiteral";
  }

  @Override
  protected void checkStringDescriptor(final Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       final StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    if (isHardCodedStringDescriptor(descriptor)) {
      if (isPropertyDescriptor(prop)) {
        if (isSetterNonNls(module.getProject(),
                           GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
                           component.getComponentClassName(), prop.getName())) {
          return;
        }
      }

      EditorQuickFixProvider provider;

      if (prop.getName().equals(BorderProperty.NAME)) {
        provider = new FixesProvider() {
          @Override
          public @NotNull QuickFix createQuickFix(GuiEditor editor, @NotNull RadComponent component12) {
            return new I18nizeFormQuickFix(editor,
                                           UIDesignerBundle.message("i18n.quickfix.border.title"),
                                           new FormBorderStringDescriptorAccessor((RadContainer)component12));
          }
        };
      }
      else if (prop.getName().equals(ITabbedPane.TAB_TITLE_PROPERTY) || prop.getName().equals(ITabbedPane.TAB_TOOLTIP_PROPERTY)) {
        provider = new FixesProvider() {
          @Override
          public @NotNull QuickFix createQuickFix(GuiEditor editor, @NotNull RadComponent component1) {
            return new I18nizeFormQuickFix(editor,
                                           UIDesignerBundle.message("i18n.quickfix.tab.title", prop.getName()),
                                           new TabTitleStringDescriptorAccessor(component1, prop.getName()));
          }
        };
      }
      else {
        provider = new FixesProvider() {
          @Override
          public @NotNull QuickFix createQuickFix(GuiEditor editor, @NotNull RadComponent component13) {
            return new I18nizeFormQuickFix(editor, UIDesignerBundle.message("i18n.quickfix.property", prop.getName()), new FormPropertyStringDescriptorAccessor(component13, (IntrospectedProperty)prop));
          }
        };
      }

      collector.addError(getID(), component, prop,
                         UIDesignerBundle.message("inspection.i18n.message.in.form", descriptor.getValue()),
                         provider);
    }
  }

  private static @NotNull LocalQuickFix @Nullable [] createBatchFixes() {
    return new LocalQuickFix[]{new I18nizeFormBatchFix()};
  }

  interface FixesProvider extends EditorQuickFixProvider, LocalQuickFixProvider {
    @Override
    default @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
      return createBatchFixes();
    }
  }

  private static boolean isPropertyDescriptor(final IProperty prop) {
    return !prop.getName().equals(BorderProperty.NAME) && !prop.getName().equals(ITabbedPane.TAB_TITLE_PROPERTY) &&
           !prop.getName().equals(ITabbedPane.TAB_TOOLTIP_PROPERTY);
  }

  private static boolean isHardCodedStringDescriptor(final StringDescriptor descriptor) {
    return !descriptor.isNoI18n() &&
           descriptor.getBundleName() == null &&
           descriptor.getKey() == null &&
           StringUtil.containsAlphaCharacters(descriptor.getValue());
  }

  private static boolean isSetterNonNls(final Project project, final GlobalSearchScope searchScope,
                                        final String componentClassName, final String propertyName) {
    PsiClass componentClass = JavaPsiFacade.getInstance(project).findClass(componentClassName, searchScope);
    if (componentClass == null) {
      return false;
    }
    PsiMethod setter = PropertyUtilBase.findPropertySetter(componentClass, propertyName, false, true);
    if (setter != null) {
      PsiParameter[] parameters = setter.getParameterList().getParameters();
      if (parameters.length == 1 &&
          "java.lang.String".equals(parameters[0].getType().getCanonicalText()) &&
          AnnotationUtil.isAnnotated(parameters [0], AnnotationUtil.NON_NLS, AnnotationUtil.CHECK_EXTERNAL)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(GuiFormFileType.INSTANCE)) {
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory != null && I18nInspection.isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory))) {
        return null;
      }
    }

    return super.checkFile(file, manager, isOnTheFly);
  }
}
