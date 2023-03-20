// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

public final class IntroStringProperty extends IntrospectedProperty<StringDescriptor> {
  private static final Logger LOG = Logger.getInstance(IntroStringProperty.class);

  /**
   * value: HashMap<String, StringDescriptor>
   */
  @NonNls
  private static final String CLIENT_PROP_NAME_2_DESCRIPTOR = "name2descriptor";

  private final StringRenderer myRenderer;
  private StringEditor myEditor;
  private final Project myProject;

  public IntroStringProperty(final String name,
                             final Method readMethod,
                             final Method writeMethod,
                             final Project project,
                             final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
    myProject = project;
    myRenderer = new StringRenderer();
  }

  @Override
  @NotNull
  public PropertyRenderer<StringDescriptor> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<StringDescriptor> getEditor() {
    if (myEditor == null) {
      myEditor = new StringEditor(myProject, this);
    }
    return myEditor;
  }

  /**
   * @return per RadComponent map between string property name and its StringDescriptor value.
   */
  @NotNull
  private static HashMap<String, StringDescriptor> getName2Descriptor(final RadComponent component){
    //noinspection unchecked
    HashMap<String, StringDescriptor> name2Descriptor = (HashMap<String, StringDescriptor>)component.getClientProperty(CLIENT_PROP_NAME_2_DESCRIPTOR);
    if(name2Descriptor == null){
      name2Descriptor = new HashMap<>();
      component.putClientProperty(CLIENT_PROP_NAME_2_DESCRIPTOR, name2Descriptor);
    }
    return name2Descriptor;
  }

  /**
   * Utility method which merge together text and mnemonic at some position
   */
  private static String mergeTextAndMnemonic(String text, final int mnemonic, final int mnemonicIndex){
    if (text == null) {
      text = "";
    }
    final int index;
    if(
      mnemonicIndex >= 0 &&
      mnemonicIndex < text.length() &&
      Character.toUpperCase(text.charAt(mnemonicIndex)) == mnemonic
    ){
      // Index really corresponds to the mnemonic
      index = mnemonicIndex;
    }
    else{
      // Mnemonic exists but index is wrong
      index = -1;
    }

    final StringBuilder buffer = new StringBuilder(text);
    if(index != -1){
      buffer.insert(index, '&');
      // Quote all '&' except inserted one
      for(int i = buffer.length() - 1; i >= 0; i--){
        if(buffer.charAt(i) == '&' && i != index){
          buffer.insert(i, '&');
        }
      }
    }
    return buffer.toString();
  }

  /**
   * It's good that method is overriden here.
   *
   * @return instance of {@link StringDescriptor}
   */
  @Override
  public StringDescriptor getValue(final RadComponent component) {
    // 1. resource bundle
    {
      final StringDescriptor descriptor = getName2Descriptor(component).get(getName());
      if(descriptor != null){
        return descriptor;
      }
    }

    // 2. plain value
    final JComponent delegee = component.getDelegee();
    return stringDescriptorFromValue(component, delegee);
  }

  private StringDescriptor stringDescriptorFromValue(final RadComponent component, final JComponent delegee) {
    final StringDescriptor result;
    if(SwingProperties.TEXT.equals(getName()) && (delegee instanceof JLabel label)){
      result = StringDescriptor.create(
        mergeTextAndMnemonic(label.getText(), label.getDisplayedMnemonic(), label.getDisplayedMnemonicIndex())
      );
    }
    else
    if(SwingProperties.TEXT.equals(getName()) && (delegee instanceof AbstractButton button)){
      result = StringDescriptor.create(
        mergeTextAndMnemonic(button.getText(), button.getMnemonic(), button.getDisplayedMnemonicIndex())
      );
    }
    else {
      if (component != null) {
        result = StringDescriptor.create((String) invokeGetter(component));
      }
      else {
        try {
          result = StringDescriptor.create((String) myReadMethod.invoke(delegee, EMPTY_OBJECT_ARRAY));
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    if (result != null) {
      // in this branch, the StringDescriptor is always a plain string, so resolve() is not necessary
      result.setResolvedValue(result.getValue());
    }
    return result;
  }

  @Override
  protected void setValueImpl(final RadComponent component, final StringDescriptor value) throws Exception {
    // 1. Put value into map
    if(value == null || (value.getBundleName() == null && !value.isNoI18n())) {
      getName2Descriptor(component).remove(getName());
    }
    else{
      getName2Descriptor(component).put(getName(), value);
    }

    // 2. Apply real string value to JComponent peer
    final JComponent delegee = component.getDelegee();

    Locale locale = (Locale)component.getClientProperty(RadComponent.CLIENT_PROP_LOAD_TIME_LOCALE);
    if (locale == null) {
      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      if (root != null) {
        locale = root.getStringDescriptorLocale();
      }
    }
    final String resolvedValue = (value != null && value.getValue() != null)
                                 ? value.getValue()
                                 : StringDescriptorManager.getInstance(component.getModule()).resolve(value, locale);

    if (value != null) {
      value.setResolvedValue(resolvedValue);
    }

    if(SwingProperties.TEXT.equals(getName())) {
      final SupportCode.TextWithMnemonic textWithMnemonic = SupportCode.parseText(resolvedValue);
      if (delegee instanceof JLabel label) {
        @NlsSafe String text = textWithMnemonic.myText;
        label.setText(text);
        if(textWithMnemonic.myMnemonicIndex != -1){
          label.setDisplayedMnemonic(textWithMnemonic.getMnemonicChar());
          label.setDisplayedMnemonicIndex(textWithMnemonic.myMnemonicIndex);
        }
        else{
          label.setDisplayedMnemonic(0);
        }
      }
      else if (delegee instanceof AbstractButton button) {
        @NlsSafe String text = textWithMnemonic.myText;
        button.setText(text);
        if(textWithMnemonic.myMnemonicIndex != -1){
          button.setMnemonic(textWithMnemonic.getMnemonicChar());
          button.setDisplayedMnemonicIndex(textWithMnemonic.myMnemonicIndex);
        }
        else{
          button.setMnemonic(0);
        }
      }
      else {
        invokeSetter(component, resolvedValue);
      }
      checkUpdateBindingFromText(component, value, textWithMnemonic);
    }
    else{
      invokeSetter(component, resolvedValue);
    }
  }

  private static void checkUpdateBindingFromText(final RadComponent component, final StringDescriptor value, final SupportCode.TextWithMnemonic textWithMnemonic) {
    if (component.isLoadingProperties()) {
      return;
    }
    // only generate binding from text if default locale is active (IDEADEV-9427)
    if (value.getValue() == null) {
      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      Locale locale = root.getStringDescriptorLocale();
      if (locale != null && locale.getDisplayName().length() > 0) {
        return;
      }
    }

    BindingProperty.checkCreateBindingFromText(component, textWithMnemonic.myText);
    if (component.getDelegee() instanceof JLabel) {
      for(IProperty prop: component.getModifiedProperties()) {
        if (prop.getName().equals(SwingProperties.LABEL_FOR) && prop instanceof IntroComponentProperty) {
          ((IntroComponentProperty) prop).updateLabelForBinding(component);
        }
      }
    }
  }

  public boolean refreshValue(RadComponent component) {
    StringDescriptor descriptor = getValue(component);
    if (descriptor.getValue() != null) return false;
    String oldResolvedValue = descriptor.getResolvedValue();
    descriptor.setResolvedValue(null);
    try {
      setValueImpl(component, descriptor);
      return !Objects.equals(oldResolvedValue, descriptor.getResolvedValue());
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
  }

  @Override
  public void write(@NotNull final StringDescriptor value, final XmlWriter writer) {
    writer.writeStringDescriptor(value,
                                 UIFormXmlConstants.ATTRIBUTE_VALUE,
                                 UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE,
                                 UIFormXmlConstants.ATTRIBUTE_KEY);
  }
}
