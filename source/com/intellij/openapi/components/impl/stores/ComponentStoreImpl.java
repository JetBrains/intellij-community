package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import com.intellij.util.DOMUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements IComponentStore {

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private Map<String, Object> myComponents = new TreeMap<String, Object>();
  private SerializationFilter mySerializationFilter = new SkipDefaultValuesSerializationFilters();
  private List<SettingsSavingComponent> mySettingsSavingComponents = new ArrayList<SettingsSavingComponent>();

  protected StateStorage getStateStorage(final Storage storageSpec) {
    throw new UnsupportedOperationException("Method getStateStorage is not supported in " + getClass());
  }

  protected StateStorage getOldStorage(final Object component) {
    throw new UnsupportedOperationException("Method getOldStorage is not supported in " + getClass());
  }

  protected StateStorage getDefaultsStorage() {
    throw new UnsupportedOperationException("Method getDefaultsStorage is not supported in " + getClass());
  }

  public void initComponent(final Object component) {
    if (component instanceof JDOMExternalizable) {
      initJdomExternalizable((JDOMExternalizable)component);
    }
    else if (component instanceof PersistentStateComponent) {
      initPersistentComponent((PersistentStateComponent<Object>)component);
    }
    else if (component instanceof SettingsSavingComponent) {
      SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
      mySettingsSavingComponents.add(settingsSavingComponent);
    }
  }


  public final void save() throws IOException {
    try {
      try {
        beforeSave();
      }
      catch (SaveCancelledException e) {
        return;
      }

      try {
        try {
          doSave();
        }
        catch (SaveCancelledException e) {
          return;
        }
      }
      finally {
        afterSave();
      }
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }


  protected void beforeSave() throws StateStorage.StateStorageException, SaveCancelledException {
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
  }

  protected void afterSave() {
    ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
  }

  protected void doSave() throws IOException {
    commit();
  }


  public void commit() {
    for (SettingsSavingComponent settingsSavingComponent : mySettingsSavingComponents) {
      settingsSavingComponent.save();
    }

    for (String name : myComponents.keySet()) {
      Object component = myComponents.get(name);
      if (component instanceof JDOMExternalizable) {
        saveJdomExternalizable((JDOMExternalizable)component);
      }
      else if (component instanceof PersistentStateComponent) {
        savePersistentComponent((PersistentStateComponent<Object>)component);
      }
    }
  }

  private void savePersistentComponent(final PersistentStateComponent<Object> persistentStateComponent) {
    try {
      Storage storageSpec = getComponentStorage(persistentStateComponent);
      StateStorage stateStorage = getStateStorage(storageSpec);

      if (stateStorage == null) return;

      org.w3c.dom.Element elementState = XmlSerializer.serialize(persistentStateComponent.getState(), DOMUtil.createDocument(), mySerializationFilter);
      stateStorage.setState(persistentStateComponent, getComponentName(persistentStateComponent), elementState);
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  private void saveJdomExternalizable(final JDOMExternalizable component) {
    final String componentName = ((BaseComponent)component).getComponentName();
    StateStorage stateStorage = getOldStorage(component);

    try {
      final Element element = new Element("temp_element");
      component.writeExternal(element);
      final org.w3c.dom.Element domElement = JDOMUtil.convertToDOM(element);
      stateStorage.setState(component, componentName, domElement);
    }
    catch (WriteExternalException ex) {
      LOG.debug(ex);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  void initJdomExternalizable(JDOMExternalizable component) {
    final String componentName = ((BaseComponent)component).getComponentName();
    myComponents.put(componentName, component);

    loadJdomDefaults(component, componentName);
    StateStorage stateStorage = getOldStorage(component);

    if (stateStorage == null) return;

    Element element = null;
    try {
      element = getJdomState(component, componentName, stateStorage);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }

    if (element != null) {
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading configuration for " + component.getClass());
        }
        component.readExternal(element);
      }
      catch (InvalidDataException e) {
        throw new InvalidComponentDataException(e);
      }
    }
  }

  private void loadJdomDefaults(final Object component, final String componentName) {
    try {
      StateStorage defaultsStorage = getDefaultsStorage();
      if (defaultsStorage == null) return;

      Element defaultState = getJdomState(component, componentName, defaultsStorage);
      if (defaultState == null) return;

      ((JDOMExternalizable)component).readExternal(defaultState);
    }
    catch (Exception e) {
      LOG.error("Cannot load defaults for " + component.getClass(), e);
    }
  }

  @Nullable
  private static Element getJdomState(final Object component, final String componentName, final StateStorage defaultsStorage) throws StateStorage.StateStorageException {
    final org.w3c.dom.Element domState = defaultsStorage.getState(component, componentName);
    if (domState == null) return null;
    return JDOMUtil.convertFromDOM(domState);
  }

  private void initPersistentComponent(final PersistentStateComponent<Object> component) {
    final String name = getComponentName(component);

    myComponents.put(name, component);

    Object state = null;
    //todo: defaults merging
    final StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      try {
        final org.w3c.dom.Element defaultState =
          defaultsStorage.getState(component, name);
        if (defaultState != null) {
          Class stateClass = getComponentStateClass(component);

          state = XmlSerializer.deserialize(defaultState, stateClass);
        }
      }
      catch (StateStorage.StateStorageException e) {
        LOG.error(e);
      }
    }

    try {
      Storage storageSpec = getComponentStorage(component);
      StateStorage stateStorage = getStateStorage(storageSpec);

      if (stateStorage == null) return;
      
      org.w3c.dom.Element elementState = stateStorage.getState(component, name);

      if (elementState != null) {
        Class stateClass = getComponentStateClass(component);

        if (state == null) {
          state = XmlSerializer.deserialize(elementState, stateClass);
        }
        else {
          XmlSerializer.deserializeInto(state, elementState);
        }
      }
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
    catch (Throwable e) {
      LOG.error(e);
    }

    if (state != null) {
      component.loadState(state);
    }
  }


  private static Class getComponentStateClass(final PersistentStateComponent<Object> persistentStateComponent) {
    final Type type =
      ReflectionUtil.resolveVariable(PersistentStateComponent.class.getTypeParameters()[0], persistentStateComponent.getClass());

    return ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(final PersistentStateComponent<Object> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    assert stateSpec != null;
    return stateSpec.name();
  }

  private static State getStateSpec(final PersistentStateComponent<Object> persistentStateComponent) {
    final State stateSpec = persistentStateComponent.getClass().getAnnotation(State.class);
    assert stateSpec != null : "No State annotation found in " + persistentStateComponent.getClass();
    return stateSpec;
  }


  @Nullable
  private static Storage getComponentStorage(final PersistentStateComponent<Object> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();
    assert storages.length <= 1 : "Multiple storage specs not supported: " + persistentStateComponent.getClass();
    return storages.length > 0 ? storages[0] : null;
  }
}
