package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.lw.StringDescriptor;
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

  @Override public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
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
    IProperty prop = findProperty(component, SwingProperties.TEXT);
    if (prop != null) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor)propValue;
        String value = ReferenceUtil.resolve(module, descriptor);
        SupportCode.TextWithMnemonic twm = SupportCode.parseText(value);
        if (twm.myMnemonicIndex >= 0 && isButtonOrLabel(module, component)) {
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
      IProperty prop = findProperty(component, SwingProperties.TEXT);
      IComponent oldComponent = myMnemonicToComponentMap.get(new Character(twm.getMnemonicChar()));
      collector.addError(prop,
                         UIDesignerBundle.message("inspection.duplicate.mnemonics.message",
                                                  getText(module, oldComponent),
                                                  getText(module, component)),
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

  private String getText(final Module module, final IComponent component) {
    IProperty prop = findProperty(component, SwingProperties.TEXT);
    StringDescriptor descriptor = (StringDescriptor) prop.getPropertyValue(component);
    return ReferenceUtil.resolve(module, descriptor);
  }

  public static IProperty findProperty(final IComponent component, final String name) {
    IProperty[] props = component.getModifiedProperties();
    for(IProperty prop: props) {
      if (prop.getName().equals(name)) return prop;
    }
    return null;
  }

  private static boolean isButtonOrLabel(final Module module, final IComponent component) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiClass aClass = psiManager.findClass(component.getComponentClassName(), scope);
    if (aClass != null) {
      final PsiClass labelClass = psiManager.findClass(JLabel.class.getName(), scope);
      if (labelClass != null && InheritanceUtil.isInheritorOrSelf(aClass, labelClass, true)) {
        return true;
      }
      final PsiClass buttonClass = psiManager.findClass(AbstractButton.class.getName(), scope);
      if (buttonClass != null && InheritanceUtil.isInheritorOrSelf(aClass, buttonClass, true)) {
        return true;
      }
    }
    return false;
  }
}
