package com.intellij.uiDesigner.inspections;

import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.propertyInspector.editors.string.StringEditorDialog;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;

/**
 * @author yole
 */
public class AssignMnemonicFix extends QuickFix {
  private static Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.inspections.AssignMnemonicFix");

  public AssignMnemonicFix(final GuiEditor editor, final RadComponent component, final String name) {
    super(editor, name, component);
  }

  public void run() {
    IProperty textProperty = FormInspectionUtil.findProperty(myComponent, SwingProperties.TEXT);
    StringDescriptor descriptor = (StringDescriptor) textProperty.getPropertyValue(myComponent);
    String value = StringDescriptorManager.getInstance(myComponent.getModule()).resolve(myComponent, descriptor);
    String[] variants = fillMnemonicVariants(SupportCode.parseText(value).myText);
    String result = Messages.showEditableChooseDialog(UIDesignerBundle.message("inspection.missing.mnemonics.quickfix.prompt"),
                                                      UIDesignerBundle.message("inspection.missing.mnemonics.quickfix.title"),
                                                      Messages.getQuestionIcon(), variants, variants [0], null);
    if (result != null) {
      if (!myEditor.ensureEditable()) {
        return;
      }
      IntroStringProperty prop = (IntroStringProperty) textProperty;
      try {
        if (descriptor.getBundleName() == null) {
          prop.setValue(myComponent, StringDescriptor.create(result));
        }
        else {
          StringEditorDialog.saveModifiedPropertyValue(myEditor.getModule(), descriptor, myEditor.getStringDescriptorLocale(), result,
                                                       myEditor.getPsiFile());
        }
        myEditor.refreshAndSave(false);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private String[] fillMnemonicVariants(final String value) {
    final StringBuffer usedMnemonics = new StringBuffer();
    RadContainer container = myComponent.getParent();
    if (container != null) {
      while (container.getParent() != null) {
        container = container.getParent();
      }
      FormEditingUtil.iterate(container, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          SupportCode.TextWithMnemonic twm = DuplicateMnemonicInspection.getTextWithMnemonic(myEditor.getModule(), component);
          if (twm != null) {
            usedMnemonics.append(twm.getMnemonicChar());
          }
          return true;
        }
      });
    }

    ArrayList<String> variants = new ArrayList<String>();
    // try upper-case and word start characters
    for(int i=0; i<value.length(); i++) {
      final char ch = value.charAt(i);
      if (i == 0 || Character.isUpperCase(ch) || (i > 0 && value.charAt(i-1) == ' ')) {
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      // try any unused characters
      for(int i=0; i<value.length(); i++) {
        final char ch = value.charAt(i);
        if (Character.isLetter(ch) && usedMnemonics.indexOf(String.valueOf(ch).toUpperCase()) < 0) {
          variants.add(value.substring(0, i) + "&" + value.substring(i));
        }
      }
    }

    if (variants.size() == 0) {
      variants.add(value);
    }
    return variants.toArray(new String[variants.size()]);
  }
}
