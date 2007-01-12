package com.intellij.openapi.components.impl;

import com.intellij.ExtensionPoints;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.ComponentDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  //todo: Introduce ComponentDescriptor instead of three maps
  private Map<Class, Object> myInterfaceToLockMap = new HashMap<Class, Object>();
  private Map<Class, Class> myInterfaceToClassMap = new HashMap<Class, Class>();
  private Map<Class, Map> myInterfaceToOptionsMap = new HashMap<Class, Map>();

  private ArrayList<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration

  private boolean myComponentsCreated = false;

  private Map<Class, Object> myInterfaceToComponentMap = new HashMap<Class, Object>();

  private Map<String, BaseComponent> myNameToComponent = new HashMap<String, BaseComponent>();
  private Map<Class, Object> myInitializedComponents = new HashMap<Class, Object>();
  private Set<Class> myLazyComponents = new HashSet<Class>();

  private MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed = false;
  private volatile boolean myDisposeCompleted = false;

  private MessageBus myMessageBus;

  private final ComponentManagerConfigurator myConfigurator = new ComponentManagerConfigurator(this);
  private ComponentManager myParentComponentManager;
  private IComponentStore myComponentStore;

  protected ComponentManagerImpl(ComponentManager parentComponentManager) {
    myParentComponentManager = parentComponentManager;
    boostrapPicoContainer();
  }

  @NotNull
  public IComponentStore getStateStore() {
    if (myComponentStore == null) {
      assert myPicoContainer != null;
      myComponentStore = (IComponentStore)myPicoContainer.getComponentInstanceOfType(IComponentStore.class);
    }
    return myComponentStore;
  }

  public MessageBus getMessageBus() {
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  private void createComponents() {
    try {
      final Class[] componentInterfaces = getComponentInterfaces();
      for (Class componentInterface : componentInterfaces) {
        if (!myLazyComponents.contains(componentInterface)) {
          try {
            createComponent(componentInterface);
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }
    }
    finally {
      myComponentsCreated = true;
    }

  }

  private synchronized Object createComponent(Class componentInterface) {
    Class componentClass = myInterfaceToClassMap.get(componentInterface);
    final Object component = instantiateComponent(componentClass);
    myInterfaceToComponentMap.put(componentInterface, component);

    if (component == null) {
      LOG.error("ComponentSetup.execute() for component " + componentInterface.getName() + " returned null");
      return null;
    }

    if (component instanceof BaseComponent) {
      BaseComponent baseComponent = (BaseComponent)component;
      final String componentName = baseComponent.getComponentName();
      if (componentName == null) {
        LOG.error("Component name is null: " + component.getClass().getName());
      }

      if (myNameToComponent.containsKey(componentName)) {
        BaseComponent loadedComponent = myNameToComponent.get(componentName);
        // component may have been already loaded by PicoContainer, so fire error only if components are really different
        if (!component.equals(loadedComponent)) {
          LOG.error("Component name collision: " + componentName + " " + loadedComponent.getClass() + " and " + component.getClass());
        }
      }
      else {
        myNameToComponent.put(componentName, baseComponent);
      }
    }

    return component;
  }

  protected void disposeComponents() {
    final Object[] components = getComponents(false);
    myDisposed = true;

    for (Object component : components) {
      if (component instanceof BaseComponent) {
        BaseComponent baseComponent = (BaseComponent)component;
        try {
          baseComponent.disposeComponent();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    myComponentsCreated = false;
  }

  @Nullable
  private synchronized <T> T getComponentFromContainer(Class<T> interfaceClass) {
    final T initializedComponent = (T)myInitializedComponents.get(interfaceClass);
    if (initializedComponent != null) return initializedComponent;

    //if (!myComponentsCreated) {
    //  LOG.error("Component requests are not allowed before they are created");
    //}

    if (!myInterfaceToClassMap.containsKey(interfaceClass)) {
      return null;
    }

    Object lock = getLock(interfaceClass);

    synchronized (lock) {
      if (myLazyComponents.contains(interfaceClass)) {
        createComponent(interfaceClass);
        myLazyComponents.remove(interfaceClass);
      }

      T component = (T)myInterfaceToComponentMap.get(interfaceClass);
      if (component == null) {
        component = (T)createComponent(interfaceClass);
      }
      if (component == null) {
        LOG.error("Cant create " + interfaceClass);
        return null;
      }

      myInitializedComponents.put(interfaceClass, component);

      return component;
    }
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    assert !myDisposeCompleted : "Already disposed";
    return getComponent(interfaceClass, null);
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    final T fromContainer = getComponentFromContainer(interfaceClass);
    if (fromContainer != null) return fromContainer;
    if (defaultImplementation != null) return defaultImplementation;
    return null;
  }

  private void initComponent(BaseComponent component, Class componentClass) {
    try {
      getStateStore().initComponent(component, componentClass);
      component.initComponent();
    }
    catch (Throwable ex) {
      handleInitComponentError(componentClass, ex, false);
    }
  }

  protected void handleInitComponentError(final Class componentClass, final Throwable ex, final boolean fatal) {
    LOG.error(ex);
  }

  private synchronized Object getLock(Class componentClass) {
    Object lock = myInterfaceToLockMap.get(componentClass);
    if (lock == null) {
      myInterfaceToLockMap.put(componentClass, lock = new Object());
    }
    return lock;
  }

  public synchronized void registerComponent(Class interfaceClass, Class implementationClass) {
    registerComponent(interfaceClass, implementationClass, null);
  }

  public synchronized void registerComponent(Class interfaceClass, Class implementationClass, Map options, boolean plugin, boolean lazy) {
    if (myInterfaceToClassMap.get(interfaceClass) != null) {
      if (plugin) {
        throw new Error("ComponentSetup for component " + interfaceClass.getName() + " already registered");
      }
      else {
        LOG.error("ComponentSetup for component " + interfaceClass.getName() + " already registered");
      }
    }

    getPicoContainer().registerComponentImplementation(implementationClass, implementationClass);

    myInterfaceToClassMap.put(interfaceClass, implementationClass);
    myComponentInterfaces.add(interfaceClass);
    myInterfaceToOptionsMap.put(interfaceClass, options);
    if (lazy) {
      myLazyComponents.add(interfaceClass);
    }
  }

  public synchronized void registerComponent(Class interfaceClass, Class implementationClass, Map options) {
    registerComponent(interfaceClass, implementationClass, options, false, false);
  }

  @NotNull
  public synchronized Class[] getComponentInterfaces() {
    return myComponentInterfaces.toArray(new Class[myComponentInterfaces.size()]);
  }

  public synchronized boolean hasComponent(@NotNull Class interfaceClass) {
    return myInterfaceToClassMap.containsKey(interfaceClass);
  }

  protected synchronized Object[] getComponents(boolean includeLazyComponents) {
    Class[] componentClasses = getComponentInterfaces();
    ArrayList<Object> components = new ArrayList<Object>(componentClasses.length);
    for (Class<?> interfaceClass : componentClasses) {
      if (includeLazyComponents || !myLazyComponents.contains(interfaceClass)) {
        Object component = getComponent(interfaceClass);
        if (component != null) components.add(component);
      }
    }
    return components.toArray(new Object[components.size()]);
  }

  @NotNull
  public synchronized <T> T[] getComponents(Class<T> baseInterfaceClass) {
    Class[] componentClasses;
    ArrayList<Class> array = new ArrayList<Class>();
    for (Class componentClass : myComponentInterfaces) {
      if (baseInterfaceClass.isAssignableFrom(componentClass)) {
        array.add(componentClass);
      }
    }
    componentClasses = array.toArray(new Class[array.size()]);
    T[] components = (T[])Array.newInstance(baseInterfaceClass, componentClasses.length);
    for (int i = 0; i < componentClasses.length; i++) {
      components[i] = (T)getComponent(componentClasses[i]);
    }
    return components;
  }

  private Object instantiateComponent(Class componentClass) {
    return getPicoContainer().getComponentInstance(componentClass);
  }

  @NotNull
  public synchronized MutablePicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  protected MutablePicoContainer createPicoContainer() {
    MutablePicoContainer result;
    if (myParentComponentManager != null) {
      result = new DefaultPicoContainer(new MyComponentAdapterFactory(this), myParentComponentManager.getPicoContainer());
    }
    else {
      result = new DefaultPicoContainer(new MyComponentAdapterFactory(this));
    }
    return result;
  }

  public void initComponentsFromExtensions(final ExtensionsArea extensionsArea) {
    //if (ApplicationManagerEx.getApplicationEx().isUnitTestMode()) return; // TODO: quick and dirty. To make tests running.
    final Application app = ApplicationManager.getApplication();
    final boolean headless = app.isHeadlessEnvironment();

    final ComponentDescriptor[] componentDescriptors =
      (ComponentDescriptor[])extensionsArea.getExtensionPoint(ExtensionPoints.COMPONENT).getExtensions();
    for (ComponentDescriptor descriptor : componentDescriptors) {
      final Map<String, String> options = descriptor.getOptionsMap();
      if (isComponentSuitable(options)) {

        ClassLoader loader = findLoader(descriptor.getPluginId());
        try {
          final String implementation = headless ? descriptor.getHeadlessImplementation() : descriptor.getImplementation();
          if (!StringUtil.isEmpty(implementation)) {
            registerComponent(Class.forName(descriptor.getInterface(), true, loader), Class.forName(implementation, true, loader), options,
                              true, isTrue(options, "lazy"));
          }
        }
        catch (Exception e) {
          LOG.error(new PluginException(e, descriptor.getPluginId()));
        }
        catch (Error e) {
          LOG.error(new PluginException(e, descriptor.getPluginId()));
        }
      }
    }

  }

  private ClassLoader findLoader(final PluginId id) {
    final Application app = ApplicationManager.getApplication();
    ClassLoader loader = app.getPlugin(id).getPluginClassLoader();
    if (loader == null) {
      loader = getClass().getClassLoader();
    }
    return loader;
  }

  public synchronized BaseComponent getComponent(String name) {
    return myNameToComponent.get(name);
  }

  public synchronized Map getComponentOptions(Class componentInterfaceClass) {
    return myInterfaceToOptionsMap.get(componentInterfaceClass);
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    return !isTrue(options, "internal") || ApplicationManagerEx.getApplicationEx().isInternal();
  }

  private static boolean isTrue(Map options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option).toString()).booleanValue();
  }

  public void saveSettingsSavingComponents() {
    Object[] components = getComponents(SettingsSavingComponent.class);
    for (Object component : components) {
      if (component instanceof SettingsSavingComponent) {
        ((SettingsSavingComponent)component).save();
      }
    }
  }

  public static class MyComponentAdapterFactory implements ComponentAdapterFactory {
    private final ComponentManagerImpl myComponentManager;

    public MyComponentAdapterFactory(final ComponentManagerImpl componentManager) {
      this.myComponentManager = componentManager;
    }

    public ComponentAdapter createComponentAdapter(final Object componentKey, Class componentImplementation, Parameter[] parameters)
      throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {

      DecoratingComponentAdapter initializingAdapter =
        new DecoratingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, true)) {
          public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException {
            Object componentInstance = null;
            try {
              componentInstance = super.getComponentInstance(picoContainer);
              if (componentInstance instanceof BaseComponent) {
                myComponentManager.initComponent((BaseComponent)componentInstance, (Class)componentKey);
              }
            }
            catch (Throwable t) {
              myComponentManager.handleInitComponentError((Class)componentKey, t, componentInstance == null);
              if (componentInstance == null) {
                System.exit(1);
              }

            }
            return componentInstance;
          }
        };

      return new CachingComponentAdapter(initializingAdapter);
    }
  }


  public synchronized void dispose() {
    final IComponentStore store = getStateStore();

    myDisposeCompleted = true;

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myComponentInterfaces = null;
    myInitializedComponents = null;
    myInterfaceToClassMap = null;
    myInterfaceToComponentMap = null;
    myInterfaceToLockMap = null;
    myInterfaceToOptionsMap = null;
    myLazyComponents = null;
    myNameToComponent = null;
    myPicoContainer = null;

    store.dispose();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  //todo[mike] there are several init* methods. Make it just 1
  public void init() {
    getStateStore().initStore();
    initComponents();
  }

  public void initComponents() {
    createComponents();
    getComponents(false);
  }

  protected void loadComponentsConfiguration(ComponentConfig[] components, @Nullable final IdeaPluginDescriptor descriptor, final boolean loadDummies) {
    myConfigurator.loadComponentsConfiguration(components, descriptor, loadDummies);
  }

  public void loadComponentsConfiguration(final String layer, final boolean loadDummies) {
    myConfigurator.loadComponentsConfiguration(layer, loadDummies);
  }

  protected void boostrapPicoContainer() {
    myPicoContainer = createPicoContainer();
    myMessageBus = MessageBusFactory.newMessageBus(this, myParentComponentManager != null ? myParentComponentManager.getMessageBus() : null);
    final MutablePicoContainer picoContainer = getPicoContainer();
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
    picoContainer.registerComponentInstance(this);
  }


  public ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }
}
