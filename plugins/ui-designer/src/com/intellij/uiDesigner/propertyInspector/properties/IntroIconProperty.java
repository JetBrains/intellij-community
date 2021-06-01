// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IconEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IconRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;


public class IntroIconProperty extends IntrospectedProperty<IconDescriptor> {
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroIconProperty_";

  private final LabelPropertyRenderer<IconDescriptor> myRenderer = new IconRenderer();
  private IconEditor myEditor;

  public IntroIconProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @Override
  public void write(@NotNull IconDescriptor value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value.getIconPath());
  }

  @Override
  @NotNull public PropertyRenderer<IconDescriptor> getRenderer() {
    return myRenderer;
  }

  @Override
  @Nullable public PropertyEditor<IconDescriptor> getEditor() {
    if (myEditor == null) {
      myEditor = new IconEditor();
    }
    return myEditor;
  }

  @Override public IconDescriptor getValue(final RadComponent component) {
    return (IconDescriptor)component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final IconDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null) {
      ensureIconLoaded(component.getModule(), value);
      invokeSetter(component, value.getIcon());
    }
    else {
      invokeSetter(component, null);
    }
  }

  public static void ensureIconLoaded(final Module module, final IconDescriptor value) {
    if (value.getIcon() == null) {
      VirtualFile iconFile = ResourceFileUtil.findResourceFileInScope(value.getIconPath(), module.getProject(),
                                                                module.getModuleWithDependenciesAndLibrariesScope(true));
      if (iconFile != null) {
        loadIconFromFile(iconFile, value);
      }
    }
  }

  public static void loadIconFromFile(final VirtualFile virtualFile, final IconDescriptor descriptor) {
    if (virtualFile != null) {
      try {
        descriptor.setIcon(new ImageIcon(virtualFile.contentsToByteArray()));
      }
      catch (Exception e1) {
        descriptor.setIcon(null);
      }
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
    super.setValueImpl(component, null);
    markTopmostModified(component, false);
  }

}
