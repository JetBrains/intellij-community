package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements IComponentStore {
  @NonNls private static final String COMPONENT_ELEMENT = "component";
  @NonNls private static final String NAME_ATTR = "name";

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private Map<String, Element> myNameToConfiguration = Collections.synchronizedMap(new HashMap<String, Element>());


  @Nullable
  protected abstract Element getDefaults(Object component) throws IOException, JDOMException, InvalidDataException;

  void clearDomMap() {
    myNameToConfiguration.clear();
  }

  void addConfiguration(String componentName, Element configuration) {
    myNameToConfiguration.put(componentName, configuration);
  }

  Set<String> getConfigurationNames() {
    return myNameToConfiguration.keySet();
  }

  @Nullable
  Element getConfiguration(String name) {
    if (myNameToConfiguration == null) return null;

    return myNameToConfiguration.get(name);
  }

  void removeConfiguration(String name) {
    myNameToConfiguration.remove(name);
  }

  void initJdomExternalizable(Object component) {
    final String componentName = ((BaseComponent)component).getComponentName();

    try {
      Element element = getDefaults(component);

      if (element != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading defaults for " + component.getClass());
        }
        ((JDOMExternalizable)component).readExternal(element);
      }
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + component.getClass(), e);
    }

    Element element = getConfiguration(componentName);

    if (element != null) {
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading configuration for " + component.getClass());
        }
        ((JDOMExternalizable)component).readExternal(element);
      }
      catch (InvalidDataException e) {
        throw new InvalidComponentDataException(e);
      }
      removeConfiguration(componentName);
    }
  }

  @Nullable
  static Element serializeComponent(Object component) {
    try {
      if (component instanceof JDOMExternalizable) {
        Element element = new Element(COMPONENT_ELEMENT);

        element.setAttribute(NAME_ATTR, ((BaseComponent)component).getComponentName());

        try {
          ((JDOMExternalizable)component).writeExternal(element);
          return element;
        }
//      catch (JDOMException exception) {
//        LOG.error(exception);
//      }
        catch (WriteExternalException ex) {
          LOG.debug(ex);
        }
      }
    }
    catch (Throwable e) { // Request #12351
      LOG.error(e);
    }
    return null;
  }

  public void initComponent(final Object component) {
    if (component instanceof JDOMExternalizable) {
      initJdomExternalizable(component);
    }
  }

  public void dispose() {
  }
}
