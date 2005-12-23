package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IComponentUtil;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * @author yole
 */
public class NoLabelForInspection extends BaseFormInspection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.inspections.NoLabelForInspection");

  public NoLabelForInspection() {
    super("NoLabelFor");
  }

  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.no.label.for");
  }

  protected void checkComponentProperties(final Module module, final IComponent component, FormErrorCollector collector) {
    if (FormInspectionUtil.isComponentClass(module, component, JTextComponent.class) ||
        FormInspectionUtil.isComponentClass(module, component, JComboBox.class)) {
      IComponent root = component;
      while(root.getParentContainer() != null) {
        root = root.getParentContainer();
      }
      final Ref<Boolean> found = new Ref<Boolean>(Boolean.FALSE);
      final Ref<RadComponent> candidateLabel = new Ref<RadComponent>();
      IComponentUtil.iterate(root, new IComponentUtil.ComponentVisitor() {
        public boolean visit(final IComponent c2) {
          if (FormInspectionUtil.isComponentClass(module, c2, JLabel.class)) {
            IProperty prop = DuplicateMnemonicInspection.findProperty(c2, SwingProperties.LABEL_FOR);
            if (prop != null && component.getId().equals(prop.getPropertyValue(c2))) {
              found.set(Boolean.TRUE);
              return false;
            }
            else if (component instanceof RadComponent &&
                     (prop == null || StringUtil.isEmpty((String)prop.getPropertyValue(c2)))) {
              RadComponent radComponent = (RadComponent) component;
              final RadComponent radComponent2 = ((RadComponent)c2);
              if (radComponent.getParent() == radComponent2.getParent()) {
                GridConstraints gc1 = radComponent.getConstraints();
                GridConstraints gc2 = radComponent2.getConstraints();
                if ((gc1.getRow() == gc2.getRow() && gc1.getColumn() == gc2.getColumn()+1) ||
                    (gc1.getColumn() == gc2.getColumn() && gc1.getRow() == gc2.getRow()+1)) {
                  candidateLabel.set(radComponent2);
                  candidateLabel.set(radComponent2);
                }
              }
            }
          }
          return true;
        }
      });
      if (!found.get().booleanValue()) {
        collector.addError(null, UIDesignerBundle.message("inspection.no.label.for.error"),
                           candidateLabel.isNull() ? null : new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new MyQuickFix(editor, component, candidateLabel.get());
                             }
                           });
      }
    }
  }

  private static class MyQuickFix extends QuickFix {
    private RadComponent myComponent;
    private RadComponent myLabel;

    public MyQuickFix(final GuiEditor editor, RadComponent component, RadComponent label) {
      super(editor, UIDesignerBundle.message("inspection.no.label.for.quickfix",
                                             ComponentTree.getComponentTitle(label)));
      myComponent = component;
      myLabel = label;
    }

    public void run() {
      final Palette palette = Palette.getInstance(myEditor.getProject());
      IntrospectedProperty[] props = palette.getIntrospectedProperties(myLabel.getComponentClass());
      for(IntrospectedProperty prop: props) {
        if (prop.getName().equals(SwingProperties.LABEL_FOR)) {
          try {
            prop.setValue(myLabel, myComponent.getId());
          }
          catch (Exception e) {
            LOG.error(e);
          }
          break;
        }
      }
    }
  }
}
