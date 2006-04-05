/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ProjectComponent;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author yole
 */
public class ClientPropertiesManager implements ProjectComponent, JDOMExternalizable {
  @NonNls private static final String ELEMENT_PROPERTIES = "properties";
  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  @NonNls private static final String ATTRIBUTE_NAME = "name";

  public static ClientPropertiesManager getInstance(final Project project) {
    return project.getComponent(ClientPropertiesManager.class);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  public String getComponentName() {
    return "ClientPropertiesManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static class ClientProperty implements Comparable {
    private String myName;
    private String myClass;

    public ClientProperty(final String name, final String aClass) {
      myName = name;
      myClass = aClass;
    }

    public String getName() {
      return myName;
    }

    public String getValueClass() {
      return myClass;
    }

    public int compareTo(final Object o) {
      ClientProperty prop = (ClientProperty) o;
      return myName.compareTo(prop.getName());
    }
  }

  private Map<String, List<ClientProperty>> myPropertyMap = new HashMap<String, List<ClientProperty>>();

  public void readExternal(Element element) throws InvalidDataException {
    myPropertyMap.clear();
    for(Object o: element.getChildren(ELEMENT_PROPERTIES)) {
      Element propertiesElement = (Element) o;
      String aClass = propertiesElement.getAttributeValue(ATTRIBUTE_CLASS);
      List<ClientProperty> classProps = new ArrayList<ClientProperty>();
      for(Object p: propertiesElement.getChildren(ELEMENT_PROPERTY)) {
        Element propertyElement = (Element) p;
        String propName = propertyElement.getAttributeValue(ATTRIBUTE_NAME);
        String propClass = propertyElement.getAttributeValue(ATTRIBUTE_CLASS);
        classProps.add(new ClientProperty(propName, propClass));
      }
      myPropertyMap.put(aClass, classProps);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for(Map.Entry<String, List<ClientProperty>> entry: myPropertyMap.entrySet()) {
      Element propertiesElement = new Element(ELEMENT_PROPERTIES);
      propertiesElement.setAttribute(ATTRIBUTE_CLASS, entry.getKey());
      for(ClientProperty prop: entry.getValue()) {
        Element propertyElement = new Element(ELEMENT_PROPERTY);
        propertyElement.setAttribute(ATTRIBUTE_NAME, prop.getName());
        propertyElement.setAttribute(ATTRIBUTE_CLASS, prop.getValueClass());
        propertiesElement.addContent(propertyElement);
      }
      element.addContent(propertiesElement);
    }
  }

  public ClientProperty[] getClientProperties(Class componentClass) {
    ArrayList<ClientProperty> result = new ArrayList<ClientProperty>();
    while(!componentClass.getName().equals(Object.class.getName())) {
      List<ClientProperty> props = myPropertyMap.get(componentClass.getName());
      if (props != null) {
        result.addAll(props);
      }
      componentClass = componentClass.getSuperclass();
    }
    Collections.sort(result);
    return result.toArray(new ClientProperty[result.size()]);
  }
}
