package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class DuplicateMnemonicInspection extends BaseFormInspection {
  private Map<MnemonicKey, IComponent> myMnemonicToComponentMap;

  public DuplicateMnemonicInspection() {
    super("DuplicateMnemonic");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return UIDesignerBundle.message("inspection.duplicate.mnemonics");
  }

  @Override public void startCheckForm(IRootContainer radRootContainer) {
    myMnemonicToComponentMap = new HashMap<MnemonicKey, IComponent>();
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
          value = StringDescriptorManager.getInstance(module).resolve(descriptor, null);
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

    MnemonicKey key = buildMnemonicKey(twm, component);
    if (myMnemonicToComponentMap.containsKey(key)) {
      IProperty prop = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
      IComponent oldComponent = myMnemonicToComponentMap.get(key);
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
      myMnemonicToComponentMap.put(key, component);
    }
  }

  private static MnemonicKey buildMnemonicKey(final SupportCode.TextWithMnemonic twm, final IComponent component) {
    List<Integer> exclusiveContainerStack = new ArrayList<Integer>();
    IContainer parent = component.getParentContainer();
    IComponent child = component;
    while(parent != null) {
      if (parent.areChildrenExclusive()) {
        exclusiveContainerStack.add(0, parent.indexOfComponent(child));
      }
      child = parent;
      parent = parent.getParentContainer();
    }
    return new MnemonicKey(twm.getMnemonicChar(), exclusiveContainerStack);
  }

  private static class MnemonicKey {
    private final char myMnemonicChar;
    private final List<Integer> myExclusiveContainerStack;

    public MnemonicKey(final char mnemonicChar, final List<Integer> exclusiveContainerStack) {
      myMnemonicChar = mnemonicChar;
      myExclusiveContainerStack = exclusiveContainerStack;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MnemonicKey that = (MnemonicKey)o;

      if (myMnemonicChar != that.myMnemonicChar) return false;
      if (!myExclusiveContainerStack.equals(that.myExclusiveContainerStack)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (int)myMnemonicChar;
      result = 31 * result + myExclusiveContainerStack.hashCode();
      return result;
    }
  }
}
