package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginDescriptor;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.ex.DecodeDefaultsUtil;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.LoadCancelledException;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Document;
import org.picocontainer.*;
import org.picocontainer.defaults.*;
import com.intellij.util.containers.HashMap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.*;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  //todo: Introduce ComponentDescriptor instead of three maps
  private Map myInterfaceToLockMap = new HashMap();
  private Map<Class, Class> myInterfaceToClassMap = new HashMap<Class, Class>();
  private Map<Class, Map> myInterfaceToOptionsMap = new HashMap<Class, Map>();

  private ArrayList<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration

  private boolean myComponentsCreated = false;

  private Map<Class, BaseComponent> myInterfaceToComponentMap = new HashMap<Class, BaseComponent>();

  private Map<String, Element> myNameToConfiguration = new HashMap<String, Element>();
  private Map<String, BaseComponent> myNameToComponent = new HashMap<String, BaseComponent>();
  private Map<Class, BaseComponent> myInitializedComponents = new HashMap<Class, BaseComponent>();
  private Set<BaseComponent> myInitializingComponents = new HashSet<BaseComponent>();
  private Set<Class> myLazyComponents = new HashSet<Class>();

  private static Map<String, Element> ourDescriptorToRootMap = new HashMap<String, Element>();
  private MutablePicoContainer myPicoContainer;

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
      for (int i = 0; i < componentInterfaces.length; i++) {
        Class componentInterface = componentInterfaces[i];
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

  private BaseComponent createComponent(Class componentInterface) {
    Class componentClass = myInterfaceToClassMap.get(componentInterface);
    final BaseComponent component = instantiateComponent(componentClass);
    myInterfaceToComponentMap.put(componentInterface, component);

    if (component == null) {
      LOG.error("ComponentSetup.execute() for component " + componentInterface.getName() + " returned null");
      return null;
    }

    final String componentName = component.getComponentName();
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
      myNameToComponent.put(componentName, component);
    }

    return component;
  }

  protected void disposeComponents() {
    final BaseComponent[] components = getComponents(false);

    for (int i = 0; i < components.length; i++) {
      BaseComponent component = components[i];
      try {
        component.disposeComponent();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    myComponentsCreated = false;
  }

  public <T> T getComponentFromContainer(Class<T> interfaceClass) {
    synchronized (this) {
      final BaseComponent initializedComponent = myInitializedComponents.get(interfaceClass);
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

      BaseComponent component = myInterfaceToComponentMap.get(interfaceClass);
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
    return getComponent(interfaceClass, null);
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    final T fromContainer = getComponentFromContainer(interfaceClass);
    if (fromContainer != null) return fromContainer;
    if (defaultImplementation != null) return defaultImplementation;
    return null;
  }

  private void initComponent(BaseComponent component, Class componentClass) {
    if (component instanceof JDOMExternalizable) {
      initJdomExternalizable(componentClass, component);
    }
    component.initComponent();
  }


  protected synchronized Object getLock(Class componentClass) {
    Object lock;
    lock = myInterfaceToLockMap.get(componentClass);
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

  public synchronized Class[] getComponentInterfaces() {
    return myComponentInterfaces.toArray(new Class[myComponentInterfaces.size()]);
  }

  public boolean hasComponent(Class interfaceClass) {
    return myInterfaceToClassMap.containsKey(interfaceClass);
  }

  protected BaseComponent[] getComponents(boolean includeLazyComponents) {
    Class[] componentClasses = getComponentInterfaces();
    ArrayList<BaseComponent> components = new ArrayList<BaseComponent>(componentClasses.length);
    for (int i = 0; i < componentClasses.length; i++) {
      Class interfaceClass = componentClasses[i];
      if (includeLazyComponents || !myLazyComponents.contains(interfaceClass)) {
        BaseComponent component = (BaseComponent)getComponent(interfaceClass);
        if (component != null) components.add(component);
      }
    }
    return components.toArray(new BaseComponent[components.size()]);
  }

  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    Class[] componentClasses;
    synchronized (this) {
      ArrayList<Class> array = new ArrayList<Class>();
      Iterator<Class> iter = myComponentInterfaces.iterator();
      while (iter.hasNext()) {
        Class componentClass = iter.next();
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
      Element node = null;

      if (component instanceof JDOMExternalizable) {
        Element element = new Element("component");

        element.setAttribute("name", component.getComponentName());

        try {
          ((JDOMExternalizable)component).writeExternal(element);
          node = element;
        }
//      catch (JDOMException exception) {
//        LOG.error(exception);
//      }
        catch (WriteExternalException ex) {
        }
      }

      return node;
    }
    catch (Throwable e) { // Request #12351
      LOG.error(e);
      return null;
    }
  }

  protected final BaseComponent instantiateComponent(Class componentClass) {
    return (BaseComponent)getPicoContainer().getComponentInstance(componentClass);
  }

  protected abstract Element getDefaults(BaseComponent component) throws IOException, JDOMException, InvalidDataException;

  protected abstract ComponentManagerImpl getParentComponentManager();


  public MutablePicoContainer getPicoContainer() {
    if (myPicoContainer == null) {
      if (getParentComponentManager() != null) {
        myPicoContainer = new DefaultPicoContainer(new MyComponentAdapterFactory(), getParentComponentManager().getPicoContainer());
      }
      else {
        myPicoContainer = new DefaultPicoContainer(new MyComponentAdapterFactory());
      }
    }
    return myPicoContainer;
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

  public void loadComponentsConfiguration(String layer) {
    loadComponentsConfiguration(ApplicationManagerEx.getApplicationEx().getComponentsDescriptor(), layer);
  }

  private void loadComponentsConfiguration(String descriptor, String layer) {
    loadComponentsConfiguration(descriptor, layer, new ArrayList<String>());
  }

  private void loadComponentsConfiguration(String descriptor, String layer, ArrayList<String> loadedIncludes) {
    try {
      Element root = ourDescriptorToRootMap.get(descriptor);
      if (root == null) {
        InputStream inputStream = DecodeDefaultsUtil.getDefaultsInputStream(this, descriptor);
        LOG.assertTrue(inputStream != null, "Defaults not found:" + descriptor);
        final Document document = JDOMUtil.loadDocument(inputStream);
        inputStream.close();

        root = document.getRootElement();
        ourDescriptorToRootMap.put(descriptor, root);
      }

      List includes = root.getChildren("include");
      if (includes != null) {
        for (Iterator iterator = includes.iterator(); iterator.hasNext();) {
          Element includeElement = (Element)iterator.next();
          String includeName = includeElement.getAttributeValue("name");

          if (includeName != null && !loadedIncludes.contains(includeName)) {
            loadedIncludes.add(includeName);
            loadComponentsConfiguration(includeName, layer, loadedIncludes);
          }
        }
      }

      loadComponentsConfiguration(root.getChild(layer));
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void loadComponentsConfiguration(final Element element) {
    loadComponentsConfiguration(element, null);
  }

  public void loadComponentsConfiguration(final Element element, PluginDescriptor descriptor) {
    if (element == null) return;

    for (Iterator i = element.getChildren().iterator(); i.hasNext();) {
      try {
        Element child = (Element)i.next();
        if ("component".equals(child.getName())) {
          String interfaceClass = child.getChildText("interface-class");
          String implClass = child.getChildText("implementation-class");

          if (interfaceClass == null) interfaceClass = implClass;

          Map<String, String> options = null;

          final List optionElements = child.getChildren("option");
          if (optionElements.size() != 0) {
            options = new HashMap<String, String>();
            for (Iterator j = optionElements.iterator(); j.hasNext();) {
              Element e = (Element)j.next();
              String name = e.getAttributeValue("name");
              String value = e.getAttributeValue("value");
              options.put(name, value);
            }
          }

          if (!isComponentSuitable(options)) continue;

          ClassLoader loader = null;
          if (descriptor != null) {
            loader = descriptor.getLoader();
          }
          if (loader == null) {
            loader = getClass().getClassLoader();
          }
          registerComponent(Class.forName(interfaceClass, true, loader), Class.forName(implClass, true, loader), options, true,
                            isTrue(options, "lazy"));
        }
      }
      catch (Exception e) {
        if (descriptor != null) {
          LOG.error(new PluginException(e, descriptor));
        }
        else {
          LOG.error(e);
        }
      }
      catch (Error e) {
        if (descriptor != null) {
          LOG.error(new PluginException(e, descriptor));
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

  private boolean isTrue(Map options, final String option) {
    return options != null && options.containsKey(option) && "true".equals(options.get(option));
  }

  protected void saveSettingsSavingComponents() {
    Object[] components = getComponents(SettingsSavingComponent.class);
    for (int i = 0; i < components.length; i++) {
      Object component = components[i];
      if (component instanceof SettingsSavingComponent) {
        ((SettingsSavingComponent)component).save();
      }
    }
  }

  private class MyComponentAdapterFactory implements ComponentAdapterFactory {
    public ComponentAdapter createComponentAdapter(final Object componentKey, Class componentImplementation, Parameter[] parameters)
      throws PicoIntrospectionException,
             AssignabilityRegistrationException,
             NotConcreteRegistrationException {
      
      DecoratingComponentAdapter initializingAdapter = new DecoratingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, true)) {
        public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException {
          Object componentInstance = super.getComponentInstance(picoContainer);
          initComponent((BaseComponent)componentInstance, (Class)componentKey);
          return componentInstance;
        }
      };

      return new CachingComponentAdapter(initializingAdapter);
    }
  }
}
