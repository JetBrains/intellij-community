package com.intellij.util;

import org.picocontainer.*;
import org.picocontainer.defaults.*;
import org.picocontainer.monitors.DefaultComponentMonitor;

public class IdeaPicoContainer extends DefaultPicoContainer {

  public IdeaPicoContainer() {
    this(null);
  }

  public IdeaPicoContainer(final PicoContainer parent) {
    super(new MyComponentAdapterFactory(), parent);
  }

  private static class MyComponentAdapterFactory extends MonitoringComponentAdapterFactory {
    private final LifecycleStrategy myLifecycleStrategy;

    public MyComponentAdapterFactory(ComponentMonitor monitor) {
      super(monitor);
      myLifecycleStrategy = new DefaultLifecycleStrategy(monitor);
    }

    public MyComponentAdapterFactory(ComponentMonitor monitor, LifecycleStrategy lifecycleStrategy) {
      super(monitor);
      myLifecycleStrategy = lifecycleStrategy;
    }

    public MyComponentAdapterFactory() {
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
}
