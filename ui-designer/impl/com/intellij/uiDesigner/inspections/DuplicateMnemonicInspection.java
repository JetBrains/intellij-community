package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class DuplicateMnemonicInspection extends BaseFormInspection {
  private Map<Character, IComponent> myMnemonicToComponentMap;

  public DuplicateMnemonicInspection() {
    super("DuplicateMnemonic");
  }

  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.duplicate.mnemonics");
  }

  @Override public void startCheckForm(IRootContainer radRootContainer) {
    myMnemonicToComponentMap = new HashMap<Character, IComponent>();
  }

  @Override public void doneCheckForm(IRootContainer rootContainer) {
    myMnemonicToComponentMap = null;
  }

  protected void checkComponentProperties(Module module, IComponent component, FormErrorCollector collector) {
    SupportCode.TextWithMnemonic twm = getTextWithMnemonic(module, component);
    if (twm != null) {
      checkTextWithMnemonic(module, component, twm, collector);
    }
  }

  @Nullable
  public static SupportCode.TextWithMnemonic getTextWithMnemonic(final Module module, final IComponent component) {
    IProperty prop = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
    if (prop != null) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor)propValue;
        String value;
        if (component instanceof RadComponent) {
          value = StringDescriptorManager.getInstance(module).resolve((RadComponent) component, descriptor);
        }
        else {
          value = StringDescriptorManager.getInstance(module).resolve(module, descriptor, null);
        }
        SupportCode.TextWithMnemonic twm = SupportCode.parseText(value);
        if (twm.myMnemonicIndex >= 0 &&
            (FormInspectionUtil.isComponentClass(module, component, JLabel.class) || FormInspectionUtil.isComponentClass(module, component, AbstractButton.class))) {
          return twm;
        }
      }
    }
    return null;
  }

  private void checkTextWithMnemonic(final Module module,
                                     final IComponent component,
                                     final SupportCode.TextWithMnemonic twm,
                                     final FormErrorCollector collector) {
    if (myMnemonicToComponentMap.containsKey(new Character(twm.getMnemonicChar()))) {
      IProperty prop = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
      IComponent oldComponent = myMnemonicToComponentMap.get(new Character(twm.getMnemonicChar()));
      collector.addError(getID(), prop,
                         UIDesignerBundle.message("inspection.duplicate.mnemonics.message",
                                                  FormInspectionUtil.getText(module, oldComponent),
                                                  FormInspectionUtil.getText(module, component)),
                         new EditorQuickFixProvider() {
                           public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                             return new AssignMnemonicFix(editor, component,
                                                          UIDesignerBundle.message("inspection.duplicate.mnemonics.quickfix"));
                           }
                         });
    }
    else {
      myMnemonicToComponentMap.put(new Character(twm.getMnemonicChar()), component);
    }
  }

}
