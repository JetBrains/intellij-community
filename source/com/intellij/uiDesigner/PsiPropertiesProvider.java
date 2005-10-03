package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.lw.*;

import java.awt.*;
import java.util.HashMap;

import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PsiPropertiesProvider implements PropertiesProvider{
  private final Module myModule;
  private final HashMap myCache;

  public PsiPropertiesProvider(final Module module){
    if (module == null){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("module cannot be null");
    }
    myModule = module;
    myCache = new HashMap();
  }

  public HashMap getLwProperties(final String className){
    if (myCache.containsKey(className)) {
      return (HashMap)myCache.get(className);
    }

    final PsiManager psiManager = PsiManager.getInstance(myModule.getProject());
    final PsiClass aClass = psiManager.findClass(className, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    if (aClass == null) {
      return null;
    }

    final HashMap result = new HashMap();

    final PsiMethod[] methods = aClass.getAllMethods();
    for (int i = 0; i < methods.length; i++) {
      final PsiMethod method = methods[i];

      // it's a setter candidate.. try to find getter

      if (!PropertyUtil.isSimplePropertySetter(method)) {
        continue;
      }
      final String name = PropertyUtil.getPropertyName(method);
      if(name == null){
        throw new IllegalStateException();
      }
      final PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, name, false, true);
      if(getter == null){
        continue;
      }

      //noinspection HardCodedStringLiteral
      if (
        name.equals("preferredSize") ||
        name.equals("minimumSize") ||
        name.equals("maximumSize")
      ){
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
      else if (String.class.getName().equals(propertyClassName)){ // java.lang.String
        property = new LwRbIntroStringProperty(name);
      }
      else if (Insets.class.getName().equals(propertyClassName)) { // java.awt.Insets
        property = new LwIntroInsetsProperty(name);
      }
      else if (Dimension.class.getName().equals(propertyClassName)) { // java.awt.Dimension
        property = new LwIntroDimensionProperty(name);
      }
      else if(Rectangle.class.getName().equals(propertyClassName)){ // java.awt.Rectangle
        property = new LwIntroRectangleProperty(name);
      }
      else {
        // type is not supported
        continue;
      }

      result.put(name, property);
    }

    myCache.put(className, result);
    return result;
  }
}
