// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.JdkApiCompatabilityCache;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.ResetValueAction;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;


@SuppressWarnings("InspectionDescriptionNotFoundInspection")
public final class Java15FormInspection extends BaseFormInspection {
  public Java15FormInspection() {
    super("Since15");
  }

  @Override
  protected void checkComponentProperties(Module module, final @NotNull IComponent component, final FormErrorCollector collector) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
    if (aClass == null) {
      return;
    }

    for(final IProperty prop: component.getModifiedProperties()) {
      final PsiMethod getter = PropertyUtilBase.findPropertyGetter(aClass, prop.getName(), false, true);
      if (getter == null) continue;
      final LanguageLevel languageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
      if (JdkApiCompatabilityCache.getInstance().firstCompatibleLanguageLevel(getter, languageLevel) != null) {
        registerError(component, collector, prop, "@since " + languageLevel.toJavaVersion().toFeatureString());
      }
    }
  }

  private void registerError(final IComponent component,
                             final FormErrorCollector collector,
                             final IProperty prop,
                             final @NonNls String api) {
    collector.addError(getID(), component, prop, UIDesignerBundle.message("inspection.java15form.problem.descriptor", api),
                       (editor, component1) -> new RemoveUIPropertyFix(editor, component1, (Property)prop));
  }

  private static class RemoveUIPropertyFix extends QuickFix {
    private final Property myProperty;

    RemoveUIPropertyFix(GuiEditor editor, RadComponent component, Property property) {
      super(editor, UIDesignerBundle.message("remove.property.quickfix"), component);
      myProperty = property;
    }


    @Override
    public void run() {
      ResetValueAction.doResetValue(Collections.singletonList(myComponent), myProperty, myEditor);
    }
  }
}
