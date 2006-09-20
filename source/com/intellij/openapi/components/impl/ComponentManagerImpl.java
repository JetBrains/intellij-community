package com.intellij.openapi.components.impl;

import com.intellij.ExtensionPoints;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.ComponentDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.*;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  //todo: Introduce ComponentDescriptor instead of three maps
  private Map<Class, Object> myInterfaceToLockMap = new HashMap<Class, Object>();
  private Map<Class, Class> myInterfaceToClassMap = new HashMap<Class, Class>();
  private Map<Class, Map> myInterfaceToOptionsMap = new HashMap<Class, Map>();

  private ArrayList<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration

  private boolean myComponentsCreated = false;

  private Map<Class, Object> myInterfaceToComponentMap = new HashMap<Class, Object>();

  private Map<String, Element> myNameToConfiguration = new HashMap<String, Element>();
  private Map<String, BaseComponent> myNameToComponent = new HashMap<String, BaseComponent>();
  private Map<Class, Object> myInitializedComponents = new HashMap<Class, Object>();
  private Set<Object> myInitializingComponents = new HashSet<Object>();
  private Set<Class> myLazyComponents = new HashSet<Class>();

  private static Map<String, Element> ourDescriptorToRootMap = new HashMap<String, Element>();
  private MutablePicoContainer myPicoContainer;
  @NonNls private static final String COMPONENT_ELEMENT = "component";
  @NonNls private static final String NAME_ATTR = "name";
  @NonNls private static final String INCLUDE_ELEMENT = "include";
  @NonNls private static final String INTERFACE_CLASS_ELEMENT = "interface-class";
  @NonNls private static final String IMPLEMENTATION_CLASS_ELEMENT = "implementation-class";
  @NonNls private static final String HEADLESS_IMPLEMENTATION_CLASS_ELEMENT = "headless-implementation-class";
  @NonNls private static final String OPTION_ELEMENT = "option";
  @NonNls private static final String VALUE_ATTR = "value";
  private boolean myDisposed = false;
  private boolean myDisposeCompleted = false;

  protected void initComponents() {
    createComponents();
    getComponents(false);
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
          catch (LoadCancelledException e) {
            throw e;
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

  private Object createComponent(Class componentInterface) {
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

  public <T> T getComponentFromContainer(Class<T> interfaceClass) {
    synchronized (this) {
      final Object initializedComponent = myInitializedComponents.get(interfaceClass);
      if (initializedComponent != null) return (T)initializedComponent;
    }

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

      Object component = myInterfaceToComponentMap.get(interfaceClass);
      if (component == null) {
        component = createComponent(interfaceClass);
      }
      if (component == null) {
        LOG.error("Cant create " + interfaceClass);
        return null;
      }

      synchronized (this) {
        if (myInitializingComponents.contains(component)) {
          LOG.error("Component  " + interfaceClass + " is being requested during its own initializing procedure");
          return (T)component;
        }

        myInitializingComponents.add(component);
      }

      try {
        //initComponent(component, interfaceClass);
      }
      finally {
        synchronized (this) {
          myInitializingComponents.remove(component);
          myInitializedComponents.put(interfaceClass, component);
        }
      }
      return (T)component;
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
      if (component instanceof JDOMExternalizable) {
        initJdomExternalizable(componentClass, component);
      }
      component.initComponent();
    }
    catch (Throwable ex) {
      handleInitComponentError(componentClass, ex, false);
    }
  }

  protected void handleInitComponentError(final Class componentClass, final Throwable ex, final boolean fatal) {
    LOG.error(ex);
  }

  protected synchronized Object getLock(Class componentClass) {
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

  public boolean hasComponent(@NotNull Class interfaceClass) {
    return myInterfaceToClassMap.containsKey(interfaceClass);
  }

  protected Object[] getComponents(boolean includeLazyComponents) {
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
  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    Class[] componentClasses;
    synchronized (this) {
      ArrayList<Class> array = new ArrayList<Class>();
      for (Class componentClass : myComponentInterfaces) {
        if (baseInterfaceClass.isAssignableFrom(componentClass)) {
          array.add(componentClass);
        }
      }
      componentClasses = array.toArray(new Class[array.size()]);
    }
    T[] components = (T[])Array.newInstance(baseInterfaceClass, componentClasses.length);
    for (int i = 0; i < componentClasses.length; i++) {
      components[i] = (T)getComponent(componentClasses[i]);
    }
    return components;
  }

  protected void initJdomExternalizable(Class componentClass, BaseComponent component) {
    doInitJdomExternalizable(componentClass, component);
  }

  public final void doInitJdomExternalizable(Class componentClass, BaseComponent component) {
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
        synchronized (this) {
          myInterfaceToComponentMap.remove(componentClass);
        }
        throw new InvalidComponentDataException(e);
      }
      removeConfiguration(component.getComponentName());
    }
  }

  protected static Element serializeComponent(BaseComponent component) {
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
        }
      }
    }
    catch (Throwable e) { // Request #12351
      LOG.error(e);
    }
    return null;
  }

  protected final Object instantiateComponent(Class componentClass) {
    return getPicoContainer().getComponentInstance(componentClass);
  }

  protected abstract Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException;

  @Nullable
  protected abstract ComponentManagerImpl getParentComponentManager();

  public MutablePicoContainer getPicoContainer() {
    if (myPicoContainer == null) {
      myPicoContainer = createPicoContainer();
    }
    return myPicoContainer;
  }

  protected MutablePicoContainer createPicoContainer() {
    MutablePicoContainer result;
    if (getParentComponentManager() != null) {
      result = new DefaultPicoContainer(new MyComponentAdapterFactory(), getParentComponentManager().getPicoContainer());
    }
    else {
      result = new DefaultPicoContainer(new MyComponentAdapterFactory());
    }
    return result;
  }

  protected void initComponentsFromExtensions(final ExtensionsArea extensionsArea) {
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

  protected static class InvalidComponentDataException extends RuntimeException {
    public InvalidComponentDataException(InvalidDataException exception) {
      super(exception);
    }
  }

  protected void clearDomMap() {
    myNameToConfiguration = new HashMap<String, Element>();
  }

  protected synchronized void addConfiguration(String componentName, Element configuration) {
    myNameToConfiguration.put(componentName, configuration);
  }

  protected synchronized Set<String> getConfigurationNames() {
    return myNameToConfiguration.keySet();
  }

  protected synchronized Element getConfiguration(String name) {
    if (myNameToConfiguration == null) return null;

    return myNameToConfiguration.get(name);
  }

  protected synchronized void removeConfiguration(String name) {
    myNameToConfiguration.remove(name);
  }

  public BaseComponent getComponent(String name) {
    return myNameToComponent.get(name);
  }

  protected Map getComponentOptions(Class componentInterfaceClass) {
    return myInterfaceToOptionsMap.get(componentInterfaceClass);
  }

  public void loadComponentsConfiguration(String layer, final boolean loadDummies) {
    loadComponentsConfiguration(ApplicationManagerEx.getApplicationEx().getComponentsDescriptor(), layer, loadDummies);
  }

  private void loadComponentsConfiguration(String descriptor, String layer, boolean loadDummies) {
    loadComponentsConfiguration(descriptor, layer, new ArrayList<String>(), loadDummies);
  }

  private void loadComponentsConfiguration(String descriptor, String layer, ArrayList<String> loadedIncludes, boolean loadDummies) {
    try {
      Element root = ourDescriptorToRootMap.get(descriptor);
      if (root == null) {
        InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(this, descriptor);
        if (inputStream == null) {
          LOG.assertTrue(false, "Defaults not found:" + descriptor);
        }
        final Document document = JDOMUtil.loadDocument(inputStream);
        inputStream.close();

        root = document.getRootElement();
        ourDescriptorToRootMap.put(descriptor, root);
      }

      List includes = root.getChildren(INCLUDE_ELEMENT);
      if (includes != null) {
        for (final Object include : includes) {
          Element includeElement = (Element)include;
          String includeName = includeElement.getAttributeValue(NAME_ATTR);

          if (includeName != null && !loadedIncludes.contains(includeName)) {
            loadedIncludes.add(includeName);
            loadComponentsConfiguration(includeName, layer, loadedIncludes, loadDummies);
          }
        }
      }

      loadComponentsConfiguration(root.getChild(layer), loadDummies);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void loadComponentsConfiguration(final Element element, boolean loadDummies) {
    loadComponentsConfiguration(element, null, loadDummies);
  }

  public void loadComponentsConfiguration(final Element element, IdeaPluginDescriptor descriptor, final boolean loadDummies) {
    if (element == null) return;
    final boolean headless = ApplicationManager.getApplication().isHeadlessEnvironment();
    for (final Object o : element.getChildren(COMPONENT_ELEMENT)) {
      @NonNls Element child = (Element)o;
      boolean skipForDummyProject = child.getChild("skipForDummyProject") != null;
      if (!loadDummies && skipForDummyProject) {
        continue;
      }
      String interfaceClass = child.getChildText(INTERFACE_CLASS_ELEMENT);
      String implClass = child.getChildText(IMPLEMENTATION_CLASS_ELEMENT);
      if (headless) {
        String headlessImplClass = child.getChildText(HEADLESS_IMPLEMENTATION_CLASS_ELEMENT);
        if (headlessImplClass != null) {
          if (headlessImplClass.trim().length() == 0) continue;
          implClass = headlessImplClass;
        }
      }

      if (interfaceClass == null) interfaceClass = implClass;

      Map<String, String> options = null;

      final List optionElements = child.getChildren(OPTION_ELEMENT);
      if (!optionElements.isEmpty()) {
        options = new HashMap<String, String>();
        for (final Object optionElement : optionElements) {
          Element e = (Element)optionElement;
          String name = e.getAttributeValue(NAME_ATTR);
          String value = e.getAttributeValue(VALUE_ATTR);
          options.put(name, value);
        }
      }

      if (!isComponentSuitable(options)) continue;

      ClassLoader loader = null;
      if (descriptor != null) {
        loader = descriptor.getPluginClassLoader();
      }
      if (loader == null) {
        loader = getClass().getClassLoader();
      }

      interfaceClass = interfaceClass.trim();
      implClass = implClass.trim();

      try {
        registerComponent(Class.forName(interfaceClass, true, loader), Class.forName(implClass, true, loader), options, true,
                          isTrue(options, "lazy"));
      }
      catch (Exception e) {
        final String message = "Error while initializing component: " + interfaceClass + ":" + implClass;

        if (descriptor != null) {
          LOG.error(message, new PluginException(e, descriptor.getPluginId()));
        }
        else {
          LOG.error(message, e);
        }
      }
      catch (Error e) {
        if (descriptor != null) {
          LOG.error(new PluginException(e, descriptor.getPluginId()));
        }
        else {
          throw e;
        }
      }
    }
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    return !isTrue(options, "internal") || ApplicationManagerEx.getApplicationEx().isInternal();
  }

  private static boolean isTrue(Map options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option).toString());
  }

  protected void saveSettingsSavingComponents() {
    Object[] components = getComponents(SettingsSavingComponent.class);
    for (Object component : components) {
      if (component instanceof SettingsSavingComponent) {
        ((SettingsSavingComponent)component).save();
      }
    }
  }

  protected class MyComponentAdapterFactory implements ComponentAdapterFactory {
    public MyComponentAdapterFactory() {
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
                initComponent((BaseComponent)componentInstance, (Class)componentKey);
              }
            }
            catch(Throwable t) {
              handleInitComponentError((Class)componentKey, t, componentInstance == null);
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


  public void dispose() {
    myDisposeCompleted = true;
    myComponentInterfaces = null;
    myInitializedComponents = null;
    myInitializingComponents = null;
    myInterfaceToClassMap = null;
    myInterfaceToComponentMap = null;
    myInterfaceToLockMap = null;
    myInterfaceToOptionsMap = null;
    myLazyComponents = null;
    myNameToComponent = null;
    myNameToConfiguration = null;
    myPicoContainer = null;
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
  