package com.intellij.util.pico;

import com.intellij.util.ReflectionCache;
import org.picocontainer.*;
import org.picocontainer.defaults.*;
import org.picocontainer.monitors.DefaultComponentMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IdeaPicoContainer extends DefaultPicoContainer {

  public IdeaPicoContainer() {
    this(null);
  }

  public IdeaPicoContainer(final PicoContainer parent) {
    super(new MyComponentAdapterFactory(), parent);
  }

  private static class MyComponentAdapterFactory extends MonitoringComponentAdapterFactory {
    private final LifecycleStrategy myLifecycleStrategy;

    private MyComponentAdapterFactory() {
      myLifecycleStrategy = new DefaultLifecycleStrategy(new DefaultComponentMonitor());
    }

    public ComponentAdapter createComponentAdapter(Object componentKey, Class componentImplementation, Parameter[] parameters)
      throws PicoIntrospectionException, AssignabilityRegistrationException, NotConcreteRegistrationException {
      return new CachingComponentAdapter(
        new ConstructorInjectionComponentAdapter(componentKey, componentImplementation, parameters, true, currentMonitor(), myLifecycleStrategy));
    }

    public void changeMonitor(ComponentMonitor monitor) {
      super.changeMonitor(monitor);
      if (myLifecycleStrategy instanceof ComponentMonitorStrategy) {
        ((ComponentMonitorStrategy)myLifecycleStrategy).changeMonitor(monitor);
      }
    }
  }



  public ComponentAdapter getComponentAdapterOfType(final Class componentType) {
    return super.getComponentAdapterOfType(componentType);
  }

  public List getComponentAdaptersOfType(final Class componentType) {
    if (componentType == null) return Collections.EMPTY_LIST;

    List<ComponentAdapter> result = new ArrayList<ComponentAdapter>();
    for (final Object o : getComponentAdapters()) {
      ComponentAdapter componentAdapter = (ComponentAdapter)o;

      if (componentAdapter instanceof AssignableToComponentAdapter) {
        AssignableToComponentAdapter assignableToComponentAdapter = (AssignableToComponentAdapter)componentAdapter;
        if (assignableToComponentAdapter.isAssignableTo(componentType)) result.add(assignableToComponentAdapter);
      }
      else if (ReflectionCache.isAssignable(componentType, componentAdapter.getComponentImplementation())) {
        result.add(componentAdapter);
      }
    }
    
    return result;
  }
}
