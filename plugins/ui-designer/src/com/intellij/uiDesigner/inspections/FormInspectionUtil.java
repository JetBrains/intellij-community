// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditorDialog;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class FormInspectionUtil {
  private FormInspectionUtil() {
  }

  static boolean isComponentClass(final Module module, final IComponent component,
                                  final Class<?> componentClass) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(component.getComponentClassName(), scope);
    if (aClass != null) {
      final PsiClass labelClass = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(componentClass.getName(), scope);
      if (labelClass != null && InheritanceUtil.isInheritorOrSelf(aClass, labelClass, true)) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable String getText(final @NotNull Module module, final IComponent component) {
    IProperty textProperty = findProperty(component, SwingProperties.TEXT);
    if (textProperty != null) {
      Object propValue = textProperty.getPropertyValue(component);
      String value = null;
      if (propValue instanceof StringDescriptor descriptor) {
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

  public static @Nullable IProperty findProperty(@NotNull IComponent component, final String name) {
    IProperty[] props = component.getModifiedProperties();
    for(IProperty prop: props) {
      if (prop.getName().equals(name)) return prop;
    }
    return null;
  }

  static void updateStringPropertyValue(GuiEditor editor,
                                        RadComponent component,
                                        IntroStringProperty prop,
                                        StringDescriptor descriptor,
                                        String result) {
    if (descriptor.getBundleName() == null) {
      prop.setValueEx(component, StringDescriptor.create(result));
    }
    else {
      final String newKeyName = StringEditorDialog.saveModifiedPropertyValue(editor.getModule(), descriptor,
                                                                             editor.getStringDescriptorLocale(), result,
                                                                             editor.getPsiFile());
      if (newKeyName != null) {
        prop.setValueEx(component, new StringDescriptor(descriptor.getBundleName(), newKeyName));
      }
    }
    editor.refreshAndSave(false);
  }
}
