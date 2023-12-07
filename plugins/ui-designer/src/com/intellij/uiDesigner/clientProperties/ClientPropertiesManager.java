// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.uiDesigner.LoaderFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "ClientPropertiesManager", defaultStateAsResource = true)
public class ClientPropertiesManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(ClientPropertiesManager.class);

  private static final @NonNls String ELEMENT_PROPERTIES = "properties";
  private static final @NonNls String ELEMENT_PROPERTY = "property";
  private static final @NonNls String ATTRIBUTE_CLASS = "class";
  private static final @NonNls String ATTRIBUTE_NAME = "name";
  private static final @NonNls String COMPONENT_NAME = "ClientPropertiesManager";

  public static ClientPropertiesManager getInstance(@NotNull Project project) {
    return project.getService(ClientPropertiesManager.class);
  }

  private static final NotNullLazyValue<ClientPropertiesManager> ourDefaultManager = NotNullLazyValue.atomicLazy(() -> {
    ClientPropertiesManager result = new ClientPropertiesManager();
    try {
      result.loadState(JDOMUtil.load(ClientPropertiesManager.class.getResourceAsStream("/" + COMPONENT_NAME + ".xml")));
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return result;
  });

  private final Map<String, List<ClientProperty>> myPropertyMap = new TreeMap<>();

  public ClientPropertiesManager() {
  }

  @NonInjectable
  private ClientPropertiesManager(@NotNull Map<String, List<ClientProperty>> propertyMap) {
    this();
    myPropertyMap.putAll(propertyMap);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public ClientPropertiesManager clone() {
    return new ClientPropertiesManager(myPropertyMap);
  }

  public void saveFrom(final ClientPropertiesManager manager) {
    myPropertyMap.clear();
    myPropertyMap.putAll(manager.myPropertyMap);
  }

  public static class ClientProperty implements Comparable {
    private final String myName;
    private final String myClass;

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

    @Override
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

  @Override
  public void loadState(@NotNull Element state) {
    myPropertyMap.clear();
    for (Element propertiesElement : state.getChildren(ELEMENT_PROPERTIES)) {
      String aClass = propertiesElement.getAttributeValue(ATTRIBUTE_CLASS);
      List<ClientProperty> classProps = new ArrayList<>();
      for (Element propertyElement : propertiesElement.getChildren(ELEMENT_PROPERTY)) {
        String propName = propertyElement.getAttributeValue(ATTRIBUTE_NAME);
        String propClass = propertyElement.getAttributeValue(ATTRIBUTE_CLASS);
        classProps.add(new ClientProperty(propName, propClass));
      }
      myPropertyMap.put(aClass, classProps);
    }
  }

  @Override
  public @Nullable Element getState() {
    Element element = new Element("state");
    if (equals(ourDefaultManager.getValue())) {
      return element;
    }

    for (Map.Entry<String, List<ClientProperty>> entry : myPropertyMap.entrySet()) {
      Element propertiesElement = new Element(ELEMENT_PROPERTIES);
      propertiesElement.setAttribute(ATTRIBUTE_CLASS, entry.getKey());
      for (ClientProperty prop : entry.getValue()) {
        Element propertyElement = new Element(ELEMENT_PROPERTY);
        propertyElement.setAttribute(ATTRIBUTE_NAME, prop.getName());
        propertyElement.setAttribute(ATTRIBUTE_CLASS, prop.getValueClass());
        propertiesElement.addContent(propertyElement);
      }
      element.addContent(propertiesElement);
    }

    return element;
  }

  public void addConfiguredProperty(final Class selectedClass, final ClientProperty enteredProperty) {
    List<ClientProperty> list = myPropertyMap.get(selectedClass.getName());
    if (list == null) {
      list = new ArrayList<>();
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

  public List<Class<?>> getConfiguredClasses(@NotNull Project project) {
    List<Class<?>> result = new ArrayList<>();
    for(String className: myPropertyMap.keySet()) {
      try {
        result.add(Class.forName(className, true, LoaderFactory.getInstance(project).getProjectClassLoader()));
      }
      catch (ClassNotFoundException e) {
        // TODO: do something better than ignore?
      }
    }
    return result;
  }

  public void addClientPropertyClass(final String className) {
    if (!myPropertyMap.containsKey(className)) {
      myPropertyMap.put(className, new ArrayList<>());
    }
  }

  public void removeClientPropertyClass(final Class<?> selectedClass) {
    myPropertyMap.remove(selectedClass.getName());
  }

  public List<ClientProperty> getConfiguredProperties(Class<?> componentClass) {
    List<ClientProperty> list = myPropertyMap.get(componentClass.getName());
    if (list == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(list);
  }

  public @NotNull List<ClientProperty> getClientProperties(Class componentClass) {
    List<ClientProperty> result = new ArrayList<>();
    while(!componentClass.getName().equals(Object.class.getName())) {
      List<ClientProperty> props = myPropertyMap.get(componentClass.getName());
      if (props != null) {
        result.addAll(props);
      }
      componentClass = componentClass.getSuperclass();
    }
    result.sort(null);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ClientPropertiesManager rhs)) {
      return false;
    }
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
