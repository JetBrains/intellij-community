package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

@SuppressWarnings({"deprecation"})
abstract class ComponentStoreImpl implements IComponentStore {

  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentStoreImpl");
  private Map<String, Object> myComponents = Collections.synchronizedMap(new TreeMap<String, Object>());
  private List<SettingsSavingComponent> mySettingsSavingComponents = Collections.synchronizedList(new ArrayList<SettingsSavingComponent>());

  @Nullable
  protected StateStorage getStateStorage(final Storage storageSpec) throws StateStorage.StateStorageException {
    throw new UnsupportedOperationException("Method getStateStorage is not supported in " + getClass());
  }

  @Nullable
  protected StateStorage getOldStorage(final Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException {
    throw new UnsupportedOperationException("Method getOldStorage is not supported in " + getClass());
  }

  protected StateStorage getDefaultsStorage() {
    throw new UnsupportedOperationException("Method getDefaultsStorage is not supported in " + getClass());
  }

  public void initComponent(final Object component) {
    boolean isSerializable =
      component instanceof JDOMExternalizable ||
      component instanceof PersistentStateComponent ||
      component instanceof SettingsSavingComponent;

    if (!isSerializable) return;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (component instanceof JDOMExternalizable) {
          initJdomExternalizable((JDOMExternalizable)component);
        }
        else if (component instanceof PersistentStateComponent) {
          initPersistentComponent((PersistentStateComponent<?>)component);
        }
        else if (component instanceof SettingsSavingComponent) {
          SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
          mySettingsSavingComponents.add(settingsSavingComponent);
        }
      }
    });
  }


  public final boolean save() throws IOException {
    try {
      try {
        beforeSave();
      }
      catch (SaveCancelledException e) {
        return false;
      }

      try {
        //noinspection EmptyCatchBlock
        try {
          doSave();
          return true;
        }
        catch (SaveCancelledException e) {
          return false;
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
    final SettingsSavingComponent[] settingsComponents =
      mySettingsSavingComponents.toArray(new SettingsSavingComponent[mySettingsSavingComponents.size()]);

    for (SettingsSavingComponent settingsSavingComponent : settingsComponents) {
      settingsSavingComponent.save();
    }

    final String[] names = myComponents.keySet().toArray(new String[myComponents.keySet().size()]);
    
    for (String name : names) {
      Object component = myComponents.get(name);
      if (component instanceof JDOMExternalizable) {
        saveJdomExternalizable((JDOMExternalizable)component);
      }
      else if (component instanceof PersistentStateComponent) {
        savePersistentComponent((PersistentStateComponent<?>)component);
      }
    }
  }

  private <T> void savePersistentComponent(final PersistentStateComponent<T> persistentStateComponent) {
    try {
      Storage[] storageSpecs = getComponentStorages(persistentStateComponent, StateStorageOperation.WRITE);

      final T state = persistentStateComponent.getState();

      for (Storage storageSpec : storageSpecs) {
        StateStorage stateStorage = getStateStorage(storageSpec);

        if (stateStorage == null) continue;


        stateStorage.setState(persistentStateComponent, getComponentName(persistentStateComponent), state);
      }
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  private void saveJdomExternalizable(final JDOMExternalizable component) {
    final String componentName = getComponentName(component);

    try {
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.WRITE);
      assert stateStorage != null;
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
      StateStorage stateStorage = getOldStorage(component, componentName, StateStorageOperation.READ);

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

  private <T> void initPersistentComponent(final PersistentStateComponent<T> component) {
    final String name = getComponentName(component);

    myComponents.put(name, component);
    if (optimizeTestLoading()) return;

    Class<T> stateClass = getComponentStateClass(component);

    T state = null;
    //todo: defaults merging
    final StateStorage defaultsStorage = getDefaultsStorage();
    if (defaultsStorage != null) {
      try {

        state = defaultsStorage.getState(component, name, stateClass, null);
      }
      catch (StateStorage.StateStorageException e) {
        LOG.error(e);
      }
    }

    try {
      Storage[] storageSpecs = getComponentStorages(component, StateStorageOperation.READ);

      for (Storage storageSpec : storageSpecs) {
        StateStorage stateStorage = getStateStorage(storageSpec);
        if (stateStorage == null) continue;
        if (!stateStorage.hasState(component, name, stateClass)) continue;

        state = stateStorage.getState(component, name, stateClass, state);
        break;
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


  private static <T> Class<T> getComponentStateClass(final PersistentStateComponent<T> persistentStateComponent) {
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

    //noinspection unchecked
    return (Class<T>)ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(final PersistentStateComponent<?> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    assert stateSpec != null;
    return stateSpec.name();
  }

  private static <T> State getStateSpec(final PersistentStateComponent<T> persistentStateComponent) {
    final State stateSpec = persistentStateComponent.getClass().getAnnotation(State.class);
    assert stateSpec != null : "No State annotation found in " + persistentStateComponent.getClass();
    return stateSpec;
  }


  @NotNull
  private <T> Storage[] getComponentStorages(final PersistentStateComponent<T> persistentStateComponent,
                                                 final StateStorageOperation operation) throws StateStorage.StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();

    if (storages.length == 1) return storages;

    assert storages.length > 0;


    final Class<StorageAnnotationsDefaultValues.NullStateStorageChooser> defaultClass =
      StorageAnnotationsDefaultValues.NullStateStorageChooser.class;

    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    final StateStorageChooser defaultStateStorageChooser = getDefaultStateStorageChooser();
    assert storageChooserClass != defaultClass || defaultStateStorageChooser != null: "State chooser not specified for: " + persistentStateComponent.getClass();

    if (storageChooserClass == defaultClass) {
      return defaultStateStorageChooser.selectStorages(storages, persistentStateComponent, operation);
    }
    else {
      try {
        //noinspection unchecked
        final StateStorageChooser<PersistentStateComponent<T>> storageChooser = storageChooserClass.newInstance();
        return storageChooser.selectStorages(storages, persistentStateComponent, operation);
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
  }

  protected boolean optimizeTestLoading() {
    return false;
  }

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return null;
  }
}
