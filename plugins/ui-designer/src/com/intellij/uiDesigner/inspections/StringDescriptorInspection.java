// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * @author yole
 */
public abstract class StringDescriptorInspection extends BaseFormInspection {
  private static final NotNullLazyValue<BorderProperty> myBorderProperty = NotNullLazyValue.lazy(new Supplier<>() {
    @Override
    public BorderProperty get() {
      return new BorderProperty(null);
    }
  });

  public StringDescriptorInspection(@NonNls String inspectionKey) {
    super(inspectionKey);
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
    for(IProperty prop: component.getModifiedProperties()) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        checkStringDescriptor(module, component, prop, descriptor, collector);
      }
    }

    if (component instanceof IContainer) {
      IContainer container = (IContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        checkStringDescriptor(module, component, myBorderProperty.getValue(), descriptor, collector);
      }
    }

    if (component.getParentContainer() instanceof ITabbedPane) {
      ITabbedPane parentTabbedPane = (ITabbedPane) component.getParentContainer();
      StringDescriptor descriptor = parentTabbedPane.getTabProperty(component, ITabbedPane.TAB_TITLE_PROPERTY);
      if (descriptor != null) {
        checkStringDescriptor(module, component, MockTabTitleProperty.INSTANCE, descriptor, collector);
      }
      descriptor = parentTabbedPane.getTabProperty(component, ITabbedPane.TAB_TOOLTIP_PROPERTY);
      if (descriptor != null) {
        checkStringDescriptor(module, component, MockTabToolTipProperty.INSTANCE, descriptor, collector);
      }
    }
  }

  protected abstract void checkStringDescriptor(final Module module,
                                                final IComponent component,
                                                final IProperty prop,
                                                final StringDescriptor descriptor,
                                                final FormErrorCollector collector);

  private static class MockTabTitleProperty implements IProperty {
    public static MockTabTitleProperty INSTANCE = new MockTabTitleProperty();

    @Override
    public String getName() {
      return ITabbedPane.TAB_TITLE_PROPERTY;
    }

    @Override
    public Object getPropertyValue(final IComponent component) {
      return null;
    }
  }

  private static class MockTabToolTipProperty implements IProperty {
    public static MockTabToolTipProperty INSTANCE = new MockTabToolTipProperty();

    @Override
    public String getName() {
      return ITabbedPane.TAB_TOOLTIP_PROPERTY;
    }

    @Override
    public Object getPropertyValue(final IComponent component) {
      return null;
    }
  }
}
