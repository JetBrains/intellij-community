package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.lw.*;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PsiPropertiesProvider implements PropertiesProvider {
  private final Module myModule;
  private final HashMap<String, HashMap> myCache;

  public PsiPropertiesProvider(@NotNull final Module module) {
    myModule = module;
    myCache = new HashMap<String, HashMap>();
  }

  @Nullable
  public HashMap getLwProperties(final String className) {
    if (myCache.containsKey(className)) {
      return myCache.get(className);
    }

    final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule);
    final PsiClass aClass = psiManager.findClass(className, scope);
    if (aClass == null) {
      return null;
    }

    final HashMap result = new HashMap();

    final PsiMethod[] methods = aClass.getAllMethods();
    for (final PsiMethod method : methods) {
      // it's a setter candidate.. try to find getter

      if (!PropertyUtil.isSimplePropertySetter(method)) {
        continue;
      }
      final String name = PropertyUtil.getPropertyName(method);
      if (name == null) {
        throw new IllegalStateException();
      }
      final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, name, false, true);
      if (getter == null) {
        continue;
      }

      //noinspection HardCodedStringLiteral
      if (
        name.equals("preferredSize") ||
        name.equals("minimumSize") ||
        name.equals("maximumSize")
        ) {
        // our own properties must be used instead
        continue;
      }

      final PsiType type = getter.getReturnType();
      final String propertyClassName = type.getCanonicalText();

      final LwIntrospectedProperty property;
      if (int.class.getName().equals(propertyClassName)) { // int
        property = new LwIntroIntProperty(name);
      }
      else if (boolean.class.getName().equals(propertyClassName)) { // boolean
        property = new LwIntroBooleanProperty(name);
      }
      else if (double.class.getName().equals(propertyClassName)) { // double
        property = new LwIntroDoubleProperty(name);
      }
      else if (float.class.getName().equals(propertyClassName)) {
        property = new LwIntroFloatProperty(name);
      }
      else if (String.class.getName().equals(propertyClassName)) { // java.lang.String
        property = new LwRbIntroStringProperty(name);
      }
      else if (Insets.class.getName().equals(propertyClassName)) { // java.awt.Insets
        property = new LwIntroInsetsProperty(name);
      }
      else if (Dimension.class.getName().equals(propertyClassName)) { // java.awt.Dimension
        property = new LwIntroDimensionProperty(name);
      }
      else if (Rectangle.class.getName().equals(propertyClassName)) { // java.awt.Rectangle
        property = new LwIntroRectangleProperty(name);
      }
      else if (Color.class.getName().equals(propertyClassName)) {
        property = new LwIntroColorProperty(name);
      }
      else if (Font.class.getName().equals(propertyClassName)) {
        property = new LwIntroFontProperty(name);
      }
      else if (Icon.class.getName().equals(propertyClassName)) {
        property = new LwIntroIconProperty(name);
      }
      else {
        PsiClass propClass = psiManager.findClass(propertyClassName, scope);
        if (propClass == null) continue;
        if (propClass.isEnum()) {
          final String enumClassName = RefactoringUtil.getInnerClassNameForClassLoader(propClass);
          final ClassLoader loader = LoaderFactory.getInstance(myModule.getProject()).getLoader(myModule);
          try {
            property = new LwIntroEnumProperty(name, loader.loadClass(enumClassName));
          }
          catch (ClassNotFoundException e) {
            continue;
          }
        }
        else {
          PsiClass componentClass = psiManager.findClass(Component.class.getName(), scope);
          PsiClass listModelClass = psiManager.findClass(ListModel.class.getName(), scope);
          if (componentClass != null && InheritanceUtil.isInheritorOrSelf(propClass, componentClass, true)) {
            property = new LwIntroComponentProperty(name);
          }
          else if (componentClass != null && listModelClass != null && InheritanceUtil.isInheritorOrSelf(propClass, listModelClass, true)) {
            property = new LwIntroListModelProperty(name, propertyClassName);
          }
          else {
            // type is not supported
            continue;
          }
        }
      }

      result.put(name, property);
    }

    myCache.put(className, result);
    return result;
  }
}
