package com.intellij.openapi.components.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.pico.AssignableToComponentAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

public class ServiceManagerImpl implements BaseComponent {
  private static final ExtensionPointName<ServiceDescriptor> APP_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.applicationService");
  private static final ExtensionPointName<ServiceDescriptor> PROJECT_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.projectService");
  private static final ExtensionPointName<ServiceDescriptor> MODULE_SERVICES = new ExtensionPointName<ServiceDescriptor>("com.intellij.moduleService");
  private ExtensionPointName<ServiceDescriptor> myExtensionPointName;
  private ExtensionPointListener<ServiceDescriptor> myExtensionPointListener;

  public ServiceManagerImpl() {
    installEP(APP_SERVICES, ApplicationManager.getApplication());
  }

  public ServiceManagerImpl(Project project) {
    installEP(PROJECT_SERVICES, project);
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public ServiceManagerImpl(Project project, Module module) {
    installEP(MODULE_SERVICES, module);
  }


  private void installEP(final ExtensionPointName<ServiceDescriptor> pointName, final ComponentManager componentManager) {
    myExtensionPointName = pointName;
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(pointName);
    assert extensionPoint != null;

    final MutablePicoContainer picoContainer = (MutablePicoContainer)componentManager.getPicoContainer();

    myExtensionPointListener = new ExtensionPointListener<ServiceDescriptor>() {
      public void extensionAdded(final ServiceDescriptor descriptor) {
        picoContainer.registerComponent(new MyComponentAdapter(descriptor));
      }

      public void extensionRemoved(final ServiceDescriptor extension) {
        picoContainer.unregisterComponent(extension.getServiceInterface());
      }
    };
    extensionPoint.addExtensionPointListener(myExtensionPointListener);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    final ExtensionPoint<ServiceDescriptor> extensionPoint = Extensions.getArea(null).getExtensionPoint(myExtensionPointName);
    assert extensionPoint != null;
    extensionPoint.removeExtensionPointListener(myExtensionPointListener);
  }

  private static class MyComponentAdapter implements AssignableToComponentAdapter {
    private ComponentAdapter myDelegate;
    private final ServiceDescriptor myDescriptor;

    public MyComponentAdapter(final ServiceDescriptor descriptor) {
      myDescriptor = descriptor;
      myDelegate = null;
    }

    public Object getComponentKey() {
      return myDescriptor.getServiceInterface();
    }

    public Class getComponentImplementation() {
      try {
        return Class.forName(myDescriptor.getServiceImplementation());
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      return getDelegate().getComponentInstance(container);
    }

    private synchronized ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(getComponentKey(), getComponentImplementation(), null, true));
      }

      return myDelegate;
    }

    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
    }

    public boolean isAssignableTo(Class aClass) {
      return aClass.getName().equals(getComponentKey());
    }
  }
}
