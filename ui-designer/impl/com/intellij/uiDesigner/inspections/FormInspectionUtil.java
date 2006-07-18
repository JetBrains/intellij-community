package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FormInspectionUtil {
  private FormInspectionUtil() {
  }

  public static boolean isComponentClass(final Module module, final IComponent component,
                                         final Class componentClass) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass != null) {
      final PsiClass labelClass = psiManager.findClass(componentClass.getName(), scope);
      if (labelClass != null && InheritanceUtil.isInheritorOrSelf(aClass, labelClass, true)) {
        return true;
      }
    }
    return false;
  }

  @Nullable public static String getText(@NotNull final Module module, final IComponent component) {
    IProperty textProperty = findProperty(component, SwingProperties.TEXT);
    if (textProperty != null) {
      Object propValue = textProperty.getPropertyValue(component);
      String value = null;
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        if (component instanceof RadComponent) {
          value = StringDescriptorManager.getInstance(module).resolve((RadComponent) component, descriptor);
        }
        else {
          value = StringDescriptorManager.getInstance(module).resolve(descriptor, null);
        }
      }
      else if (propValue instanceof String) {
        value = (String) propValue;
      }
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  public static IProperty findProperty(final IComponent component, final String name) {
    IProperty[] props = component.getModifiedProperties();
    for(IProperty prop: props) {
      if (prop.getName().equals(name)) return prop;
    }
    return null;
  }
}
