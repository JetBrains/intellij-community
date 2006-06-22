/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class ClientPropertiesManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.clientProperties.ClientPropertiesManager");

  @NonNls private static final String ELEMENT_PROPERTIES = "properties";
  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String COMPONENT_NAME = "ClientPropertiesManager";

  public static ClientPropertiesManager getInstance(final Project project) {
    return project.getComponent(ClientPropertiesManager.class);
  }

  private static ClientPropertiesManager ourDefaultManager;

  private final Map<String, List<ClientProperty>> myPropertyMap = new TreeMap<String, List<ClientProperty>>();

  public ClientPropertiesManager() {
  }

  private ClientPropertiesManager(final Map<String, List<ClientProperty>> propertyMap) {
    myPropertyMap.putAll(propertyMap);
  }

  public ClientPropertiesManager clone() {
    return new ClientPropertiesManager(myPropertyMap);
  }

  public void saveFrom(final ClientPropertiesManager manager) {
    myPropertyMap.clear();
    myPropertyMap.putAll(manager.myPropertyMap);
  }

  public void projectOpened() {
    checkInitDefaultManager();
  }

  private static void checkInitDefaultManager() {
    if (ourDefaultManager == null) {
      ourDefaultManager = new ClientPropertiesManager();
      try {
        //noinspection HardCodedStringLiteral
        final Document document = new SAXBuilder().build(ClientPropertiesManager.class.getResource("/" + COMPONENT_NAME + ".xml"));
        //noinspection HardCodedStringLiteral
        final Element child = document.getRootElement();
        ourDefaultManager.readExternal(child);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void projectClosed() {
  }

  @NotNull @NonNls
  public String getComponentName() {
    return COMPONENT_NAME;
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

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClientProperty that = (ClientProperty)o;

      if (!myClass.equals(that.myClass)) return false;
      if (!myName.equals(that.myName)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = myName.hashCode();
      result = 31 * result + myClass.hashCode();
      return result;
    }
  }

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
    if (equals(ourDefaultManager)) {
      throw new WriteExternalException();
    }
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

  public void addConfiguredProperty(final Class selectedClass, final ClientProperty enteredProperty) {
    List<ClientProperty> list = myPropertyMap.get(selectedClass.getName());
    if (list == null) {
      list = new ArrayList<ClientProperty>();
      myPropertyMap.put(selectedClass.getName(), list);
    }
    list.add(enteredProperty);
  }

  public void removeConfiguredProperty(final Class selectedClass, final String name) {
    List<ClientProperty> list = myPropertyMap.get(selectedClass.getName());
    if (list != null) {
      for(ClientProperty prop: list) {
        if (prop.getName().equals(name)) {
          list.remove(prop);
          break;
        }
      }
    }
  }

  public List<Class> getConfiguredClasses() {
    List<Class> result = new ArrayList<Class>();
    for(String className: myPropertyMap.keySet()) {
      try {
        result.add(Class.forName(className));
      }
      catch (ClassNotFoundException e) {
        // TODO: do something better than ignore?
      }
    }
    return result;
  }

  public void addClientPropertyClass(final String className) {
    if (!myPropertyMap.containsKey(className)) {
      myPropertyMap.put(className, new ArrayList<ClientProperty>());
    }
  }

  public void removeClientPropertyClass(final Class selectedClass) {
    myPropertyMap.remove(selectedClass.getName());
  }

  public ClientProperty[] getConfiguredProperties(Class componentClass) {
    final List<ClientProperty> list = myPropertyMap.get(componentClass.getName());
    if (list == null) return new ClientProperty[0]; 
    return list.toArray(new ClientProperty[list.size()]);
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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ClientPropertiesManager)) {
      return false;
    }
    ClientPropertiesManager rhs = (ClientPropertiesManager) obj;
    if (rhs.myPropertyMap.size() != myPropertyMap.size()) {
      return false;
    }
    for(Map.Entry<String, List<ClientProperty>> entry: myPropertyMap.entrySet()) {
      List<ClientProperty> rhsList = rhs.myPropertyMap.get(entry.getKey());
      if (rhsList == null || rhsList.size() != entry.getValue().size()) {
        return false;
      }

      for(ClientProperty prop: entry.getValue()) {
        if (!rhsList.contains(prop)) {
          return false;
        }
      }
    }
    return true;
  }
}
