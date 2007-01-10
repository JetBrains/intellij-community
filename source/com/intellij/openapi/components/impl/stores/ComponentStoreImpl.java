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
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements StateStore, IComponentStore {
  @NonNls private static final String COMPONENT_ELEMENT = "component";
  @NonNls private static final String NAME_ATTR = "name";

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private Map<String, Element> myNameToConfiguration = new HashMap<String, Element>();


  @Nullable
  protected abstract Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException;

  synchronized void clearDomMap() {
    myNameToConfiguration = new HashMap<String, Element>();
  }

  synchronized void addConfiguration(String componentName, Element configuration) {
    myNameToConfiguration.put(componentName, configuration);
  }

  synchronized Set<String> getConfigurationNames() {
    return myNameToConfiguration.keySet();
  }

  @Nullable
  synchronized Element getConfiguration(String name) {
    if (myNameToConfiguration == null) return null;

    return myNameToConfiguration.get(name);
  }

  private synchronized void removeConfiguration(String name) {
    myNameToConfiguration.remove(name);
  }

  void initJdomExternalizable(Class componentClass, BaseComponent component) {
    try {
      Element element = getDefaults(component);

      if (element != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading defaults for " + componentClass.getName());
        }
        ((JDOMExternalizable)component).readExternal(element);
      }
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + componentClass.getName(), e);
    }

    Element element = getConfiguration(component.getComponentName());

    if (element != null) {
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading configuration for " + componentClass.getName());
        }
        ((JDOMExternalizable)component).readExternal(element);
      }
      catch (InvalidDataException e) {
        throw new InvalidComponentDataException(e);
      }
      removeConfiguration(component.getComponentName());
    }
  }

  @Nullable
  static Element serializeComponent(BaseComponent component) {
    try {
      if (component instanceof JDOMExternalizable) {
        Element element = new Element(COMPONENT_ELEMENT);

        element.setAttribute(NAME_ATTR, component.getComponentName());

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

  public void initComponent(final BaseComponent component, final Class componentClass) {
    if (component instanceof JDOMExternalizable) {
      initJdomExternalizable(componentClass, component);
    }
  }

  public void dispose() {
  }
}
