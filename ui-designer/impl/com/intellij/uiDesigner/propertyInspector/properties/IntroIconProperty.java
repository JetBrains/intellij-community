package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IconEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroIconProperty extends IntrospectedProperty {
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroIconProperty_";

  private LabelPropertyRenderer myRenderer = new LabelPropertyRenderer() {
    protected void customize(Object value) {
      if (value != null) {
        IconDescriptor descriptor = (IconDescriptor) value;
        setIcon(descriptor.getIcon());
        setText(descriptor.getIconPath());
      }
    }
  };

  private IconEditor myEditor;

  public IntroIconProperty(final String name, final Method readMethod, final Method writeMethod) {
    super(name, readMethod, writeMethod);
    myEditor = new IconEditor();
  }

  public void write(@NotNull Object value, XmlWriter writer) {
    IconDescriptor descriptor = (IconDescriptor) value;
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, descriptor.getIconPath());
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override public Object getValue(final RadComponent component) {
    return component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    IconDescriptor descriptor = (IconDescriptor) value;
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), descriptor);
    if (descriptor != null) {
      if (descriptor.getIcon() == null) {
        PsiFile iconFile = ModuleUtil.findResourceFileInDependents(component.getModule(),
                                                                   descriptor.getIconPath(),
                                                                   PsiFile.class);
        if (iconFile != null) {
          loadIconFromFile(iconFile, descriptor);          
        }
      }
      super.setValueImpl(component, descriptor.getIcon());
    }
  }

  public static void loadIconFromFile(final PsiFile file, final IconDescriptor descriptor) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      try {
        descriptor.setIcon(new ImageIcon(virtualFile.contentsToByteArray()));
      }
      catch (Exception e1) {
        descriptor.setIcon(null);
      }
    }
  }
}
