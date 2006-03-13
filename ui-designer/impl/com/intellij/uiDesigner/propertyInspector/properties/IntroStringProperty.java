package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.StringRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroStringProperty extends IntrospectedProperty{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.IntroStringProperty");

  /**
   * value: HashMap<String, StringDescriptor>
   */
  @NonNls
  private static final String CLIENT_PROP_NAME_2_DESCRIPTOR = "name2descriptor";

  private final StringRenderer myRenderer;
  private final StringEditor myEditor;

  public IntroStringProperty(final String name, final Method readMethod, final Method writeMethod, final Project project) {
    super(name, readMethod, writeMethod);
    myRenderer = new StringRenderer();
    myEditor = new StringEditor(project);
  }

  @NotNull
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }

  /**
   * @return per RadComponent map between string property name and its StringDescriptor value.
   */
  @NotNull
  private static HashMap<String, StringDescriptor> getName2Descriptor(final RadComponent component){
    HashMap<String, StringDescriptor> name2Descriptor = (HashMap<String, StringDescriptor>)component.getClientProperty(CLIENT_PROP_NAME_2_DESCRIPTOR);
    if(name2Descriptor == null){
      name2Descriptor = new HashMap<String,StringDescriptor>();
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

    final StringBuffer buffer = new StringBuffer(text);
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
  public Object getValue(final RadComponent component) {
    // 1. resource bundle
    {
      final StringDescriptor descriptor = getName2Descriptor(component).get(getName());
      if(descriptor != null){
        return descriptor;
      }
    }

    // 2. plain value
    final JComponent delegee = component.getDelegee();
    return stringDescriptorFromValue(delegee);
  }

  private StringDescriptor stringDescriptorFromValue(final JComponent delegee) {
    final StringDescriptor result;
    if(SwingProperties.TEXT.equals(getName()) && (delegee instanceof JLabel)){
      final JLabel label = (JLabel)delegee;
      result = StringDescriptor.create(
        mergeTextAndMnemonic(label.getText(), label.getDisplayedMnemonic(), label.getDisplayedMnemonicIndex())
      );
    }
    else
    if(SwingProperties.TEXT.equals(getName()) && (delegee instanceof AbstractButton)){
      final AbstractButton button = (AbstractButton)delegee;
      result = StringDescriptor.create(
        mergeTextAndMnemonic(button.getText(), button.getMnemonic(), button.getDisplayedMnemonicIndex())
      );
    }
    else{
      try {
        result = StringDescriptor.create((String) myReadMethod.invoke(delegee, EMPTY_OBJECT_ARRAY));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    if (result != null) {
      // in this branch, the StringDescriptor is always a plain string, so resolve() is not necessary
      result.setResolvedValue(result.getValue());
    }
    return result;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    // 1. Put value into map
    final StringDescriptor descriptor = (StringDescriptor)value;
    if(descriptor == null || (descriptor.getBundleName() == null && !descriptor.isNoI18n())) {
      getName2Descriptor(component).remove(getName());
    }
    else{
      getName2Descriptor(component).put(getName(), descriptor);
    }

    // 2. Apply real string value to JComponent peer
    final JComponent delegee = component.getDelegee();
    final String resolvedValue = (descriptor != null && descriptor.getValue() != null)
                                 ? descriptor.getValue()
                                 : ReferenceUtil.resolve(component, descriptor);

    if (descriptor != null) {
      descriptor.setResolvedValue(resolvedValue);
    }

    if(SwingProperties.TEXT.equals(getName())) {
      final SupportCode.TextWithMnemonic textWithMnemonic = SupportCode.parseText(resolvedValue);
      BindingProperty.checkCreateBindingFromText(component, textWithMnemonic.myText);
      if (delegee instanceof JLabel) {
        final JLabel label = (JLabel)delegee;
        label.setText(textWithMnemonic.myText);
        if(textWithMnemonic.myMnemonicIndex != -1){
          label.setDisplayedMnemonic(textWithMnemonic.getMnemonicChar());
          label.setDisplayedMnemonicIndex(textWithMnemonic.myMnemonicIndex);
        }
        else{
          label.setDisplayedMnemonic(0);
        }
      }
      else if (delegee instanceof AbstractButton) {
        final AbstractButton button = (AbstractButton)delegee;
        button.setText(textWithMnemonic.myText);
        if(textWithMnemonic.myMnemonicIndex != -1){
          button.setMnemonic(textWithMnemonic.getMnemonicChar());
          button.setDisplayedMnemonicIndex(textWithMnemonic.myMnemonicIndex);
        }
        else{
          button.setMnemonic(0);
        }
      }
      else {
        super.setValueImpl(component, resolvedValue);
      }
    }
    else{
      super.setValueImpl(component, resolvedValue);
    }
  }

  public void refreshValue(RadComponent component) {
    StringDescriptor descriptor = (StringDescriptor) getValue(component);
    descriptor.setResolvedValue(null);
    try {
      setValueImpl(component, descriptor);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void write(@NotNull final Object value, final XmlWriter writer) {
    final StringDescriptor descriptor = (StringDescriptor)value;
    writer.writeStringDescriptor(descriptor,
                                 UIFormXmlConstants.ATTRIBUTE_VALUE,
                                 UIFormXmlConstants.ATTRIBUTE_RESOURCE_BUNDLE,
                                 UIFormXmlConstants.ATTRIBUTE_KEY);
  }

  @Override public void importSnapshotValue(final JComponent component, final RadComponent radComponent) {
    try {
      Object value = myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
      if (value != null) {
        setValue(radComponent, stringDescriptorFromValue(component));
      }
    }
    catch (Exception e) {
      // ignore
    }
  }
}
