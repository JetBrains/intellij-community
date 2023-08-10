// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.uiDesigner.lw.LwXmlReader;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class Properties {
  private static final Logger LOG = Logger.getInstance(Properties.class);

  private final Map<String, String> myClass2InplaceProperty;
  private final Map<String, Set<String>> myClass2ExpertProperties;
  private final Map<String, Map<String, IntEnumEditor.Pair[]>> myClass2EnumProperties;
  private final Map<String, Set<String>> myClass2DeprecatedProperties;

  public static Properties getInstance() {
    return ApplicationManager.getApplication().getService(Properties.class);
  }

  public Properties() {
    myClass2InplaceProperty = new HashMap<>();
    myClass2ExpertProperties = new HashMap<>();
    myClass2EnumProperties = new HashMap<>();
    myClass2DeprecatedProperties = new HashMap<>();

    try (InputStream inputStream = Properties.class.getResourceAsStream("/gui-designer-properties.xml")) {
      if (inputStream != null) {
        loadState(JDOMUtil.load(inputStream));
      }
    }
    catch (JDOMException | IOException e) {
      LOG.error(e);
    }
  }

  /**
   * @return it is possible that properties do not exist in class; returned values are ones specified in config. Never null
   */
  public boolean isExpertProperty(final Module module, @NotNull final Class aClass, final String propertyName) {
    for (Class c = aClass; c != null; c = c.getSuperclass()) {
      final Set<String> properties = myClass2ExpertProperties.get(c.getName());
      if (properties != null && properties.contains(propertyName)) {
        return true;
      }
    }
    return isPropertyDeprecated(module, aClass, propertyName);
  }

  public boolean isPropertyDeprecated(final Module module, final Class aClass, final String propertyName) {
    // TODO[yole]: correct module-dependent caching
    Set<String> deprecated = myClass2DeprecatedProperties.get(aClass.getName());
    if (deprecated == null) {
      deprecated = new HashSet<>();
      PsiClass componentClass =
        JavaPsiFacade.getInstance(module.getProject()).findClass(aClass.getName(), module.getModuleWithDependenciesAndLibrariesScope(true));
      if (componentClass != null) {
        PsiMethod[] methods = componentClass.getAllMethods();
        for (PsiMethod method : methods) {
          if (method.isDeprecated() && PropertyUtilBase.isSimplePropertySetter(method)) {
            deprecated.add(PropertyUtilBase.getPropertyNameBySetter(method));
          }
        }
      }
      myClass2DeprecatedProperties.put(aClass.getName(), deprecated);
    }

    return deprecated.contains(propertyName);
  }

  /**
   * @return it is possible that property does not exist in class; returned value is one specified in config
   */
  @Nullable
  public String getInplaceProperty(final Class aClass) {
    for (Class c = aClass; c != null; c = c.getSuperclass()) {
      final String property = myClass2InplaceProperty.get(c.getName());
      if (property != null) {
        return property;
      }
    }
    return null;
  }

  public IntEnumEditor.Pair @Nullable [] getEnumPairs(final Class aClass, final String name) {
    for (Class c = aClass; c != null; c = c.getSuperclass()) {
      final Map<String, IntEnumEditor.Pair[]> map = myClass2EnumProperties.get(c.getName());
      if (map != null) {
        return map.get(name);
      }
    }
    return null;
  }

  private void loadState(@NotNull Element state) {
    for (Element classElement : state.getChildren("class")) {
      final String className = LwXmlReader.getRequiredString(classElement, "name");

      // Read "expert" properties
      final Element expertPropertiesElement = classElement.getChild("expert-properties");
      if (expertPropertiesElement != null) {
        Set<String> expertProperties = new HashSet<>();
        for (Element e : expertPropertiesElement.getChildren("property")) {
          expertProperties.add(LwXmlReader.getRequiredString(e, "name"));
        }

        myClass2ExpertProperties.put(className, expertProperties);
      }

      // Read "inplace" property. This property is optional
      final Element inplacePropertyElement = classElement.getChild("inplace-property");
      if (inplacePropertyElement != null) {
        myClass2InplaceProperty.put(className, LwXmlReader.getRequiredString(inplacePropertyElement, "name"));
      }

      final Element enumPropertyElement = classElement.getChild("enum-properties");
      if (enumPropertyElement != null) {
        loadEnumProperties(className, enumPropertyElement);
      }
    }
  }

  private void loadEnumProperties(final String className, final Element enumPropertyElement) {
    Map<String, IntEnumEditor.Pair[]> map = new HashMap<>();
    for (Element e : enumPropertyElement.getChildren("property")) {
      final String name = LwXmlReader.getRequiredString(e, Constants.NAME);
      final List list = e.getChildren("constant");
      IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[list.size()];
      for (int i = 0; i < list.size(); i++) {
        Element constant = (Element)list.get(i);
        int value = LwXmlReader.getRequiredInt(constant, Constants.VALUE);
        String message = constant.getAttributeValue("message");
        String text = (message != null)
                      ? UIDesignerBundle.message(message)
                      : LwXmlReader.getRequiredString(constant, Constants.NAME);
        pairs[i] = new IntEnumEditor.Pair(value, text);
      }
      map.put(name, pairs);
    }
    if (map.size() > 0) {
      myClass2EnumProperties.put(className, map);
    }
  }
}
