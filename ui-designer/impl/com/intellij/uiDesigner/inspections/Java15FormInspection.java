package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.actions.ResetValueAction;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.quickFixes.QuickFix;

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
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass == null) {
      return;
    }

    for(final IProperty prop: component.getModifiedProperties()) {
      final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, prop.getName(), false, true);
      if (Java15APIUsageInspection.isJava15APIUsage(getter)) {
        collector.addError(getID(), prop,
                           InspectionsBundle.message("inspection.1.5.problem.descriptor", "@since 1.5"),
                           new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new RemovePropertyFix(editor, component, (Property) prop);
                             }
                           });
      }
    }
  }

  private static class RemovePropertyFix extends QuickFix {
    private Property myProperty;

    public RemovePropertyFix(GuiEditor editor, RadComponent component, Property property) {
      super(editor, UIDesignerBundle.message("remove.property.quickfix"), component);
      myProperty = property;
    }


    public void run() {
      ResetValueAction.doResetValue(Collections.singletonList(myComponent), myProperty, myEditor);
    }
  }
}
