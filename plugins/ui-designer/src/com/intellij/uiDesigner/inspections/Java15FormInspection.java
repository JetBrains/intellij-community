/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.module.Module;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.ResetValueAction;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;

/**
 * @author yole
 */
public class Java15FormInspection extends BaseFormInspection {
  public Java15FormInspection() {
    super("Since15");
  }

  protected void checkComponentProperties(Module module, final IComponent component, final FormErrorCollector collector) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
    if (aClass == null) {
      return;
    }

    for(final IProperty prop: component.getModifiedProperties()) {
      final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
      InspectionProfileEntry profileEntry =
        InspectionProjectProfileManager.getInstance(aClass.getProject()).getInspectionProfile()
          .getInspectionTool(Java15APIUsageInspection.SHORT_NAME, aClass);
      if (profileEntry instanceof LocalInspectionToolWrapper) {
        profileEntry = ((LocalInspectionToolWrapper) profileEntry).getTool();
      }
      final Java15APIUsageInspection tool = (Java15APIUsageInspection)profileEntry;
      if (Java15APIUsageInspection.isForbiddenApiUsage(getter, tool.API)) {
        registerError(component, collector, prop, "@since " + tool.getApiPresentable());
      }
    }
  }

  private void registerError(final IComponent component,
                             final FormErrorCollector collector,
                             final IProperty prop,
                             @NonNls final String api) {
    collector.addError(getID(), component, prop, InspectionsBundle.message("inspection.1.5.problem.descriptor", api),
                       new EditorQuickFixProvider() {
                         public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                           return new RemovePropertyFix(editor, component, (Property)prop);
                         }
                       });
  }

  private static class RemovePropertyFix extends QuickFix {
    private final Property myProperty;

    public RemovePropertyFix(GuiEditor editor, RadComponent component, Property property) {
      super(editor, UIDesignerBundle.message("remove.property.quickfix"), component);
      myProperty = property;
    }


    public void run() {
      ResetValueAction.doResetValue(Collections.singletonList(myComponent), myProperty, myEditor);
    }
  }
}
