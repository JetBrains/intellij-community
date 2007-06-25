package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
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
  @Nullable private SaveSessionImpl mySession;


  protected abstract StateStorageManager getStateStorageManager();

  @Deprecated
  @Nullable
  private StateStorage getStateStorage(@NotNull final Storage storageSpec) throws StateStorage.StateStorageException {
    return getStateStorageManager().getStateStorage(storageSpec);
  }

  @Deprecated
  @Nullable
  private StateStorage getOldStorage(final Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException {
    return getStateStorageManager().getOldStorage(component, componentName, operation);
  }

  protected StateStorage getDefaultsStorage() {
    throw new UnsupportedOperationException("Method getDefaultsStorage is not supported in " + getClass());
  }

  public void initComponent(@NotNull final Object component) {
    boolean isSerializable = component instanceof JDOMExternalizable || component instanceof PersistentStateComponent || component instanceof SettingsSavingComponent;

    if (!isSerializable) return;

    if (component instanceof SettingsSavingComponent) {
      SettingsSavingComponent settingsSavingComponent = (SettingsSavingComponent)component;
      mySettingsSavingComponents.add(settingsSavingComponent);
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (component instanceof JDOMExternalizable) {
          initJdomExternalizable((JDOMExternalizable)component);
        }
        else if (component instanceof PersistentStateComponent) {
          initPersistentComponent((PersistentStateComponent<?>)component);
        }
      }
    });
  }


  @Nullable
  public SaveSession startSave() throws IOException {
    try {
      assert mySession == null;
      mySession = createSaveSession();
      mySession.commit();
      return mySession;
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new SaveSessionImpl();
  }

  public void finishSave(final SaveSession saveSession) {
    assert mySession == saveSession;
    mySession.finishSave();
    mySession = null;
  }

  private <T> void commitPersistentComponent(@NotNull final PersistentStateComponent<T> persistentStateComponent, @NotNull StateStorageManager.ExternalizationSession session) {
    try {
      Storage[] storageSpecs = getComponentStorageSpecs(persistentStateComponent, StateStorageOperation.WRITE);


      final T state = persistentStateComponent.getState();

      session.setState(storageSpecs, persistentStateComponent, getComponentName(persistentStateComponent), state);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  private static void commitJdomExternalizable(@NotNull final JDOMExternalizable component, @NotNull StateStorageManager.ExternalizationSession session) {
    final String componentName = getComponentName(component);

    try {
      session.setStateInOldStorage(component, componentName, component);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  void initJdomExternalizable(@NotNull JDOMExternalizable component) {
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

    if (element == null) return;

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

  private static String getComponentName(@NotNull final JDOMExternalizable component) {
    if ((component instanceof BaseComponent)) {
      return ((BaseComponent)component).getComponentName();
    }
    else {
      return component.getClass().getName();
    }
  }

  private void loadJdomDefaults(@NotNull final Object component, final String componentName) {
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
  private static Element getJdomState(final Object component, final String componentName, @NotNull final StateStorage defaultsStorage) throws StateStorage.StateStorageException {
    return defaultsStorage.getState(component, componentName, Element.class, null);
  }

  private <T> void initPersistentComponent(@NotNull final PersistentStateComponent<T> component) {
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
      Storage[] storageSpecs = getComponentStorageSpecs(component, StateStorageOperation.READ);

      for (Storage storageSpec : storageSpecs) {
        StateStorage stateStorage = getStateStorage(storageSpec);
        if (stateStorage == null || !stateStorage.hasState(component, name, stateClass)) continue;

        state = stateStorage.getState(component, name, stateClass, state);
      }
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error("Error while initializing: " + component, e);
    }
    catch (Throwable e) {
      LOG.error("Error while initializing: " + component, e);
    }

    if (state != null) {
      component.loadState(state);
    }
  }


  @NotNull
  private static <T> Class<T> getComponentStateClass(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class persistentStateComponentClass = PersistentStateComponent.class;
    Class componentClass = persistentStateComponent.getClass();

    nextSuperClass:
    while (true) {
      final Class[] interfaces = ReflectionCache.getInterfaces(componentClass);

      for (Class anInterface : interfaces) {
        if (anInterface.equals(persistentStateComponentClass)) {
          break nextSuperClass;
        }
      }

      componentClass = componentClass.getSuperclass();
    }

    final Type type = ReflectionUtil.resolveVariable(persistentStateComponentClass.getTypeParameters()[0], componentClass);

    //noinspection unchecked
    return (Class<T>)ReflectionUtil.getRawType(type);
  }

  private static String getComponentName(@NotNull final PersistentStateComponent<?> persistentStateComponent) {
    final State stateSpec = getStateSpec(persistentStateComponent);
    assert stateSpec != null;
    return stateSpec.name();
  }

  private static <T> State getStateSpec(@NotNull final PersistentStateComponent<T> persistentStateComponent) {
    final Class<? extends PersistentStateComponent> aClass = persistentStateComponent.getClass();
    final State stateSpec = aClass.getAnnotation(State.class);
    assert stateSpec != null : "No State annotation found in " + aClass;
    return stateSpec;
  }


  @NotNull
  private <T> Storage[] getComponentStorageSpecs(@NotNull final PersistentStateComponent<T> persistentStateComponent,
                                                 final StateStorageOperation operation) throws StateStorage.StateStorageException {
    final State stateSpec = getStateSpec(persistentStateComponent);

    final Storage[] storages = stateSpec.storages();

    if (storages.length == 1) return storages;

    assert storages.length > 0;


    final Class<StorageAnnotationsDefaultValues.NullStateStorageChooser> defaultClass =
      StorageAnnotationsDefaultValues.NullStateStorageChooser.class;

    final Class<? extends StateStorageChooser> storageChooserClass = stateSpec.storageChooser();
    final StateStorageChooser defaultStateStorageChooser = getDefaultStateStorageChooser();
    assert storageChooserClass != defaultClass || defaultStateStorageChooser != null :
      "State chooser not specified for: " + persistentStateComponent.getClass();

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

  protected class SaveSessionImpl implements SaveSession {
    @Nullable protected StateStorageManager.SaveSession myStorageManagerSaveSession;

    public SaveSessionImpl() {
      ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    }

    public Collection<String> getUsedMacros() throws StateStorage.StateStorageException {
      return myStorageManagerSaveSession.getUsedMacros();
    }

    public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException {
      try {
        return myStorageManagerSaveSession.getAllStorageFilesToSave();
      }
      catch (StateStorage.StateStorageException e) {
        throw new IOException(e.getMessage());
      }
    }

    public SaveSession save() throws IOException {
      try {
        final SettingsSavingComponent[] settingsComponents =
          mySettingsSavingComponents.toArray(new SettingsSavingComponent[mySettingsSavingComponents.size()]);

        for (SettingsSavingComponent settingsSavingComponent : settingsComponents) {
          settingsSavingComponent.save();
        }


        myStorageManagerSaveSession.save();
      }
      catch (StateStorage.StateStorageException e) {
        LOG.info(e);
        throw new IOException(e.getMessage());
      }

      return this;
    }

    public void finishSave() {
      getStateStorageManager().finishSave(myStorageManagerSaveSession);
      myStorageManagerSaveSession = null;
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
      mySession = null;
    }

    protected void commit() throws StateStorage.StateStorageException {
      final StateStorageManager storageManager = getStateStorageManager();

      final StateStorageManager.ExternalizationSession session = storageManager.startExternalization();

      final String[] names = myComponents.keySet().toArray(new String[myComponents.keySet().size()]);

      for (String name : names) {
        Object component = myComponents.get(name);
        if (component instanceof JDOMExternalizable) {
          commitJdomExternalizable((JDOMExternalizable)component, session);
        }
        else if (component instanceof PersistentStateComponent) {
          commitPersistentComponent((PersistentStateComponent<?>)component, session);
        }
      }
      myStorageManagerSaveSession = storageManager.startSave(session);
    }

    @Nullable
    public Set<String> analyzeExternalChanges(final Set<VirtualFile> changedFiles) {
      return myStorageManagerSaveSession.analyzeExternalChanges(changedFiles);
    }
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    return getStateStorageManager().getAllStorageFiles();
  }

  public boolean reload(final Set<VirtualFile> changedFiles) throws IOException, StateStorage.StateStorageException {
    final SaveSessionImpl saveSession = (SaveSessionImpl)startSave();


    final Set<String> componentNames = saveSession.analyzeExternalChanges(changedFiles);
    try {
      if (componentNames == null) return false;

      if (!isReloadPossible(componentNames)) return false;
    }
    finally {
      finishSave(saveSession);
    }

    doReload(changedFiles, componentNames);

    reinitComponents(componentNames, changedFiles);

    return true;
  }

  protected boolean isReloadPossible(final Set<String> componentNames) {
    for (String componentName : componentNames) {
      final Object component = myComponents.get(componentName);

      if (component != null && !(component instanceof PersistentStateComponent)) return false;
    }

    return true;
  }

  protected void reinitComponents(final Set<String> componentNames, final Set<VirtualFile> changedFiles) {
    final List<VirtualFile> storageFiles = getAllStorageFiles(false);
    for (VirtualFile storageFile : storageFiles) {
      if (changedFiles.contains(storageFile)) {
        for (String componentName : componentNames) {
          final PersistentStateComponent component = (PersistentStateComponent)myComponents.get(componentName);
          if (component != null) {
            initPersistentComponent(component);
          }
        }
        break;
      }
    }
  }

  protected void doReload(final Set<VirtualFile> changedFiles, final Set<String> componentNames) throws StateStorage.StateStorageException {
    getStateStorageManager().reload(changedFiles, componentNames);
  }
}
