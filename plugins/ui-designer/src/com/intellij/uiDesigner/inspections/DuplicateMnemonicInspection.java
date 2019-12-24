/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
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

  @Nullable
  static SupportCode.TextWithMnemonic getTextWithMnemonic(final Module module, final IComponent component) {
    if (module.isDisposed()) return null;
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
                         (EditorQuickFixProvider)(editor, component1) -> new AssignMnemonicFix(editor, component1,
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
      int result = (int)myMnemonicChar;
      result = 31 * result + myExclusiveContainerStack.hashCode();
      return result;
    }
  }

  private static class MnemonicMap extends HashMap<MnemonicKey, IComponent> {
  }
}
