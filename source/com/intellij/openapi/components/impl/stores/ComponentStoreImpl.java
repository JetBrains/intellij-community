package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

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
  private List<SettingsSavingComponent> mySettingsSavingComponents = new ArrayList<SettingsSavingComponent>();

  @Nullable
  protected StateStorage getStateStorage(final Storage storageSpec) throws StateStorage.StateStorageException {
    throw new UnsupportedOperationException("Method getStateStorage is not supported in " + getClass());
  }

  protected StateStorage getOldStorage(final Object component) throws StateStorage.StateStorageException {
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
      Storage storageSpec = getComponentStorage(persistentStateComponent, StateStorageChooser.Operation.WRITE);
      StateStorage stateStorage = getStateStorage(storageSpec);

      if (stateStorage == null) return;

      final Object state = persistentStateComponent.getState();

      stateStorage.setState(persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  private void saveJdomExternalizable(final JDOMExternalizable component) {
    final String componentName = getComponentName(component);

    try {
      StateStorage stateStorage = getOldStorage(component);
      stateStorage.setState(component, componentName, component);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  void initJdomExternalizable(JDOMExternalizable component) {
    final String componentName = getComponentName(component);

    myComponents.put(componentName, component);

    if (optimizeTestLoading()) return;

    loadJdomDefaults(component, componentName);

    Element element = null;
    try {
      StateStorage stateStorage = getOldStorage(component);

      if (stateStorage == null) return;
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

  private static String getComponentName(final JDOMExternalizable component) {
    final String componentName;

    if (!(component instanceof BaseComponent)) {
      componentName = component.getClass().getName();
    }
    else {
      componentName = ((BaseComponent)component).getComponentName();
    }
    return componentName;
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
    return defaultsStorage.getState(component, componentName, Element.class, null);
  }

  private void initPersistentComponent(final PersistentStateComponent<Object> component) {
    final String name = getComponentName(component);

    myComponents.put(name, component);
    if (optimizeTestLoading()) return;

    Object state = null;
    //todo: defaults merging
    final StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      try {

        Class stateClass = getComponentStateClass(component);
        state = defaultsStorage.getState(component, name, stateClass, null);
      }
      catch (StateStorage.StateStorageException e) {
        LOG.error(e);
      }
    }

    try {
      Storage storageSpec = getComponentStorage(component, StateStorageChooser.Operation.READ);
      StateStorage stateStorage = getStateStorage(storageSpec);

      if (stateStorage == null) return;

      Class stateClass = getComponentStateClass(component);
      state = stateStorage.getState(component, name, stateClass, state);
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
    final Class persistentStateComponentClass = PersistentStateComponent.class;
    Class componentClass = persistentStateComponent.getClass();

    nextSuperClass: while (true) {
      final Class[] interfaces = ReflectionCache.getInterfaces(componentClass);

      for (Class anInterface : interfaces) {
        if (anInterface.equals(persistentStateComponentClass)) {
          break nextSuperClass;
        }
      }

      componentClass = componentClass.getSuperclass();
    }

    final Type type =
      ReflectionUtil.resolveVariable(persistentStateComponentClass.getTypeParameters()[0], componentClass);

    return ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(final PersistentStateComponent<Object> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    assert stateSpec != null;
    return stateSpec.name();
  }

  private static <T> State getStateSpec(final PersistentStateComponent<T> persistentStateComponent) {
    final State stateSpec = persistentStateComponent.getClass().getAnnotation(State.class);
    assert stateSpec != null : "No State annotation found in " + persistentStateComponent.getClass();
    return stateSpec;
  }


  @Nullable
  private static <T> Storage getComponentStorage(final PersistentStateComponent<T> persistentStateComponent,
                                                 final StateStorageChooser.Operation operation) throws StateStorage.StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();

    if (storages.length == 1) return storages[0];

    assert storages.length > 0;


    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    assert storageChooserClass != StorageAnnotationsDefaultValues.NullStateStorageChooser.class : "State chooser not specified for: " + persistentStateComponent.getClass();

    try {
      final StateStorageChooser<PersistentStateComponent<T>> storageChooser = storageChooserClass.newInstance();
      return storageChooser.selectStorage(storages, persistentStateComponent, operation);
    }
    catch (InstantiationException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IllegalAccessException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  protected boolean optimizeTestLoading() {
    return false;
  }
}
