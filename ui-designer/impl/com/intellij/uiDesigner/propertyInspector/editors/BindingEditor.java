package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadErrorComponent;
import com.intellij.uiDesigner.radComponents.RadHSpacer;
import com.intellij.uiDesigner.radComponents.RadVSpacer;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BindingEditor extends ComboBoxPropertyEditor<String> {

  public BindingEditor(final Project project) {
    myCbx.setEditable(true);
    final JComponent editorComponent = (JComponent)myCbx.getEditor().getEditorComponent();
    editorComponent.setBorder(null);

    myCbx.addActionListener(
      new ActionListener(){
        public void actionPerformed(final ActionEvent e){
          fireValueCommitted(true, false);
        }
      }
    );

    new AnAction(){
      public void actionPerformed(final AnActionEvent e) {
        if (!myCbx.isPopupVisible()) {
          fireEditingCancelled();
          SwingUtilities.invokeLater(
            new Runnable(){
              public void run(){
                UIDesignerToolWindowManager.getInstance(project).getPropertyInspector().requestFocus();
              }
            }
          );
        }
      }
    }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myCbx);
  }

  private static String[] getFieldNames(final RadComponent component, final String currentName) {
    final ArrayList<String> result = new ArrayList<String>();
    if (currentName != null){
      result.add(currentName);
    }

    final IRootContainer root = FormEditingUtil.getRoot(component);
    final String className = root.getClassToBind();
    if (className == null) {
      return result.toArray(new String[result.size()]);
    }

    final PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), className);
    if (aClass == null) {
      return result.toArray(new String[result.size()]);
    }

    final PsiField[] fields = aClass.getFields();

    for (final PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      final String fieldName = field.getName();

      if (Comparing.equal(currentName, fieldName)) {
        continue;
      }

      if (!FormEditingUtil.isBindingUnique(component, fieldName, root)) {
        continue;
      }

      final String componentClassName;
      if (component instanceof RadErrorComponent) {
        componentClassName = component.getComponentClassName();
      }
      else if (component instanceof RadHSpacer || component instanceof RadVSpacer) {
        componentClassName = Spacer.class.getName();
      }
      else {
        componentClassName = component.getComponentClass().getName();
      }

      final PsiType componentType;
      try {
        componentType =
          PsiManager.getInstance(component.getModule().getProject()).getElementFactory().createTypeFromText(componentClassName, null);
      }
      catch (IncorrectOperationException e) {
        continue;
      }

      final PsiType fieldType = field.getType();
      if (!fieldType.isAssignableFrom(componentType)) {
        continue;
      }

      result.add(fieldName);
    }

    String text = FormInspectionUtil.getText(component.getModule(), component);
    if (text != null) {
      String binding = BindingProperty.suggestBindingFromText(component, text);
      if (binding != null && !result.contains(binding)) {
        result.add(binding);        
      }
    }

    final String[] names = result.toArray(new String[result.size()]);
    Arrays.sort(names);
    return names;
  }

  public String getValue() throws Exception {
    final String value = super.getValue();
    return value != null ? value.replace('$', '.') : null; // PSI works only with dots
  }

  public JComponent getComponent(final RadComponent component, final String value, final boolean inplace){
    final String[] fieldNames = getFieldNames(component, value);
    myCbx.setModel(new DefaultComboBoxModel(fieldNames));
    myCbx.setSelectedItem(value);
    return myCbx;
  }
}
