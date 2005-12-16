package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class StringDescriptorInspection extends BaseFormInspection {
  protected enum StringDescriptorType { PROPERTY, BORDER, TAB }
  private static BorderProperty myBorderProperty = new BorderProperty();

  public StringDescriptorInspection(@NonNls String inspectionKey) {
    super(inspectionKey);
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    for(IProperty prop: component.getModifiedProperties()) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        checkStringDescriptor(StringDescriptorType.PROPERTY, module, component, prop, descriptor, collector);
      }
    }

    if (component instanceof IContainer) {
      IContainer container = (IContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        checkStringDescriptor(StringDescriptorType.BORDER, module, component, myBorderProperty, descriptor, collector);
      }
    }

    if (component.getParentContainer() instanceof ITabbedPane) {
      ITabbedPane parentTabbedPane = (ITabbedPane) component.getParentContainer();
      final StringDescriptor descriptor = parentTabbedPane.getTabTitle(component);
      if (descriptor != null) {
        checkStringDescriptor(StringDescriptorType.TAB, module, component, null, descriptor, collector);
      }
    }
  }

  protected abstract void checkStringDescriptor(final StringDescriptorType descriptorType,
                                                final Module module,
                                                final IComponent component,
                                                final IProperty prop,
                                                final StringDescriptor descriptor,
                                                final FormErrorCollector collector);
}
