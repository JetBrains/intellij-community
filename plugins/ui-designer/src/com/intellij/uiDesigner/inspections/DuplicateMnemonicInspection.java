// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class DuplicateMnemonicInspection extends BaseFormInspection {
  private static final ThreadLocal<HashMap<IRootContainer, MnemonicMap>> myContainerMnemonicMap = ThreadLocal.withInitial(HashMap::new);

  public DuplicateMnemonicInspection() {
    super("DuplicateMnemonic");
  }

  @Override public void startCheckForm(IRootContainer radRootContainer) {
    myContainerMnemonicMap.get().put(radRootContainer, new MnemonicMap());
  }

  @Override public void doneCheckForm(IRootContainer rootContainer) {
    myContainerMnemonicMap.get().remove(rootContainer);
  }

  @Override
  protected void checkComponentProperties(Module module, @NotNull IComponent component, FormErrorCollector collector) {
    SupportCode.TextWithMnemonic twm = getTextWithMnemonic(module, component);
    if (twm != null) {
      checkTextWithMnemonic(module, component, twm, collector);
    }
  }

  static @Nullable SupportCode.TextWithMnemonic getTextWithMnemonic(final Module module, final IComponent component) {
    if (module.isDisposed()) return null;
    IProperty prop = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
    if (prop != null) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor descriptor) {
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
                                     @NotNull IComponent component,
                                     final SupportCode.TextWithMnemonic twm,
                                     final FormErrorCollector collector) {
    IRootContainer root = FormEditingUtil.getRoot(component);
    MnemonicMap map = myContainerMnemonicMap.get().get(root);
    MnemonicKey key = buildMnemonicKey(twm, component);
    if (map.containsKey(key)) {
      IProperty prop = FormInspectionUtil.findProperty(component, SwingProperties.TEXT);
      IComponent oldComponent = map.get(key);
      collector.addError(getID(), component, prop,
                         UIDesignerBundle.message("inspection.duplicate.mnemonics.message",
                                                  FormInspectionUtil.getText(module, oldComponent),
                                                  FormInspectionUtil.getText(module, component)),
                         (editor, component1) -> new AssignMnemonicFix(editor, component1,
                                                                                               UIDesignerBundle.message("inspection.duplicate.mnemonics.quickfix")));
    }
    else {
      map.put(key, component);
    }
  }

  private static MnemonicKey buildMnemonicKey(final SupportCode.TextWithMnemonic twm, final IComponent component) {
    List<Integer> exclusiveContainerStack = new ArrayList<>();
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

    MnemonicKey(final char mnemonicChar, final List<Integer> exclusiveContainerStack) {
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
      int result = myMnemonicChar;
      result = 31 * result + myExclusiveContainerStack.hashCode();
      return result;
    }
  }

  private static class MnemonicMap extends HashMap<MnemonicKey, IComponent> {
  }
}
