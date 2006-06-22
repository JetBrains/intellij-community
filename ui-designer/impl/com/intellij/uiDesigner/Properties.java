package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.lw.LwXmlReader;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Properties implements ApplicationComponent, JDOMExternalizable{
  private final HashMap<String,String> myClass2InplaceProperty;
  private final HashMap<String,HashSet<String>> myClass2ExpertProperties;
  private Map<String, Map<String, IntEnumEditor.Pair[]>> myClass2EnumProperties;
  private Map<String, Set<String>> myClass2DeprecatedProperties;

  public static Properties getInstance() {
    return ApplicationManager.getApplication().getComponent(Properties.class);
  }

  public Properties(){
    myClass2InplaceProperty = new HashMap<String,String>();
    myClass2ExpertProperties = new HashMap<String,HashSet<String>>();
    myClass2EnumProperties = new HashMap<String, Map<String, IntEnumEditor.Pair[]>>();
    myClass2DeprecatedProperties = new HashMap<String, Set<String>>();
  }

  /**
   * @return it is possible that properties do not exist in class; returned values are ones specified in config. Never null
   */
  public boolean isExpertProperty(final Module module, @NotNull final Class aClass, final String propertyName) {
    for (Class c = aClass; c != null; c = c.getSuperclass()){
      final HashSet<String> properties = myClass2ExpertProperties.get(c.getName());
      if (properties != null && properties.contains(propertyName)){
        return true;
      }
    }
    return isPropertyDeprecated(module, aClass, propertyName);
  }

  public boolean isPropertyDeprecated(final Module module, final Class aClass, final String propertyName) {
    // TODO[yole]: correct module-dependent caching
    Set<String> deprecated = myClass2DeprecatedProperties.get(aClass.getName());
    if (deprecated == null) {
      deprecated = new HashSet<String>();
      PsiClass componentClass = PsiManager.getInstance(module.getProject()).findClass(aClass.getName(), module.getModuleWithDependenciesAndLibrariesScope(true));
      if (componentClass != null) {
        PsiMethod[] methods = componentClass.getAllMethods();
        for(PsiMethod method: methods) {
          if (method.isDeprecated() && PropertyUtil.isSimplePropertySetter(method)) {
            deprecated.add(PropertyUtil.getPropertyNameBySetter(method));
          }
        }
      }
    }

    return deprecated.contains(propertyName);
  }

  /**
   * @return it is possible that property does not exist in class; returned value is one specified in config
   */
  @Nullable
  public String getInplaceProperty(final Class aClass) {
    for (Class c = aClass; c != null; c = c.getSuperclass()){
      final String property = myClass2InplaceProperty.get(c.getName());
      if (property != null){
        return property;
      }
    }
    return null;
  }

  @Nullable
  public IntEnumEditor.Pair[] getEnumPairs(final Class aClass, final String name) {
    for (Class c = aClass; c != null; c = c.getSuperclass()) {
      final Map<String, IntEnumEditor.Pair[]> map = myClass2EnumProperties.get(c.getName());
      if (map != null) {
        return map.get(name);
      }
    }
    return null;
  }

  @NotNull
  public String getComponentName(){
    return "gui-designer-properties";
  }

  public void initComponent() { }

  public void disposeComponent() { }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(final Element element) {
    for (final Object classObject : element.getChildren("class")) {
      final Element classElement = (Element)classObject;

      final String className = LwXmlReader.getRequiredString(classElement, "name");

      // Read "expert" properties
      final Element expertPropertiesElement = classElement.getChild("expert-properties");
      if (expertPropertiesElement != null) {
        final HashSet<String> expertProperties = new HashSet<String>();

        for (final Object o : expertPropertiesElement.getChildren("property")) {
          final Element e = (Element)o;
          final String name = LwXmlReader.getRequiredString(e, "name");
          expertProperties.add(name);
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void loadEnumProperties(final String className, final Element enumPropertyElement) {
    Map<String, IntEnumEditor.Pair[]> map = new HashMap<String, IntEnumEditor.Pair[]>();
    for(final Object o: enumPropertyElement.getChildren("property")) {
      final Element e = (Element) o;
      final String name = LwXmlReader.getRequiredString(e, "name");
      final List list = e.getChildren("constant");
      IntEnumEditor.Pair[] pairs = new IntEnumEditor.Pair[list.size()];
      for(int i=0; i<list.size(); i++) {
        Element constant = (Element) list.get(i);
        int value = LwXmlReader.getRequiredInt(constant, "value");
        String message = constant.getAttributeValue("message");
        String text = (message != null)
                      ? UIDesignerBundle.message(message)
                      : LwXmlReader.getRequiredString(constant, "name");
        pairs [i] = new IntEnumEditor.Pair(value, text);
      }
      map.put(name, pairs);
    }
    if (map.size() > 0) {
      myClass2EnumProperties.put(className, map);
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException{
    throw new WriteExternalException();
  }
}