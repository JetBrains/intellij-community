package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.uiDesigner.lw.LwXmlReader;
import org.jdom.Element;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Properties implements ApplicationComponent, JDOMExternalizable{
  private final HashMap<String,String> myClass2InplaceProperty;
  private final HashMap<String,HashSet<String>> myClass2ExpertProperties;

  public static Properties getInstance() {
    return ApplicationManager.getApplication().getComponent(Properties.class);
  }
  
  public Properties(){
    myClass2InplaceProperty = new HashMap<String,String>();
    myClass2ExpertProperties = new HashMap<String,HashSet<String>>();
  }

  /**
   * @return it is possible that properties do not exist in class; returned values are ones specified in config. Never null
   */ 
  public boolean isExpertProperty(final Class aClass, final String propertyName) {
    for (Class c = aClass; c != null; c = c.getSuperclass()){
      final HashSet<String> properties = myClass2ExpertProperties.get(c.getName());
      if (properties != null && properties.contains(propertyName)){
        return true;
      }
    }
    return false;
  }

  /**
   * @return it is possible that property does not exist in class; returned value is one specified in config
   */ 
  public String getInplaceProperty(final Class aClass) {
    for (Class c = aClass; c != null; c = c.getSuperclass()){
      final String property = myClass2InplaceProperty.get(c.getName());
      if (property != null){
        return property;
      }
    }
    return null;
  }
  
  public String getComponentName(){
    return "gui-designer-properties";
  }

  public void initComponent() { }

  public void disposeComponent() { }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(final Element element) {
    final Iterator i = element.getChildren("class").iterator();
    while (i.hasNext()) {
      final Element classElement = (Element)i.next();

      final String className = LwXmlReader.getRequiredString(classElement, "name");

      // Read "expert" properties
      final Element expertPropertiesElement = classElement.getChild("expert-properties");
      if (expertPropertiesElement != null) {
        final HashSet<String> expertProperties = new HashSet<String>();

        final Iterator iterator = expertPropertiesElement.getChildren("property").iterator();
        while (iterator.hasNext()) {
          final Element e = (Element)iterator.next();
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
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException{
    throw new WriteExternalException();
  }
}