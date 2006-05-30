package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroComponentProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;

/**
 * @author yole
 */
public class NoLabelForInspection extends BaseFormInspection {
  public NoLabelForInspection() {
    super("NoLabelFor");
  }

  @Override public String getDisplayName() {
    return UIDesignerBundle.message("inspection.no.label.for");
  }

  protected void checkComponentProperties(final Module module, final IComponent component, FormErrorCollector collector) {
    ComponentItem item = Palette.getInstance(module.getProject()).getItem(component.getComponentClassName());
    if (item != null && item.isCanAttachLabel()) {
      IComponent root = component;
      while(root.getParentContainer() != null) {
        root = root.getParentContainer();
      }
      final Ref<Boolean> found = new Ref<Boolean>(Boolean.FALSE);
      final Ref<RadComponent> candidateLabel = new Ref<RadComponent>();
      FormEditingUtil.iterate(root, new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent c2) {
          if (FormInspectionUtil.isComponentClass(module, c2, JLabel.class)) {
            IProperty prop = FormInspectionUtil.findProperty(c2, SwingProperties.LABEL_FOR);
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
        collector.addError(getID(), null, UIDesignerBundle.message("inspection.no.label.for.error"),
                           candidateLabel.isNull() ? null : new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new MyQuickFix(editor, component, candidateLabel.get());
                             }
                           });
      }
    }
  }

  private static class MyQuickFix extends QuickFix {
    private RadComponent myLabel;

    public MyQuickFix(final GuiEditor editor, RadComponent component, RadComponent label) {
      super(editor, UIDesignerBundle.message("inspection.no.label.for.quickfix",
                                             ComponentTree.getComponentTitle(label)), component);
      myLabel = label;
    }

    public void run() {
      if (!myEditor.ensureEditable()) {
        return;
      }
      final Palette palette = Palette.getInstance(myEditor.getProject());
      IntrospectedProperty[] props = palette.getIntrospectedProperties(myLabel);
      for(IntrospectedProperty prop: props) {
        if (prop.getName().equals(SwingProperties.LABEL_FOR) && prop instanceof IntroComponentProperty) {
          IntroComponentProperty icp = (IntroComponentProperty) prop;
          icp.setValueEx(myLabel, myComponent.getId());
          myEditor.refreshAndSave(false);
          break;
        }
      }
    }
  }
}
