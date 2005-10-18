package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.core.Spacer;
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
public final class BindingEditor extends ComboBoxPropertyEditor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.propertyInspector.editors.BindingEditor");

  private final GuiEditor myEditor;

  public BindingEditor(final GuiEditor editor){
    LOG.assertTrue(editor != null);

    myEditor = editor;
    myCbx.setEditable(true);
    final JComponent editorComponent = (JComponent)myCbx.getEditor().getEditorComponent();
    editorComponent.setBorder(null);

    myCbx.addActionListener(
      new ActionListener(){
        public void actionPerformed(final ActionEvent e){
          fireValueCommited();
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
                myEditor.getPropertyInspector().requestFocus();
              }
            }
          );
        }
      }
    }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myCbx);
  }

  private String[] getFieldNames(final RadComponent component, final String currentName) {
    final ArrayList<String> result = new ArrayList<String>();
    if (currentName != null){
      result.add(currentName);
    }

    final String className = myEditor.getRootContainer().getClassToBind();
    if (className == null) {
      return result.toArray(new String[result.size()]);
    }

    final PsiClass aClass = FormEditingUtil.findClassToBind(myEditor.getModule(), className);
    if (aClass == null) {
      return result.toArray(new String[result.size()]);
    }

    final PsiField[] fields = aClass.getFields();

    for (int i = 0; i < fields.length; i++) {
      final PsiField field = fields[i];

      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      final String fieldName = field.getName();

      if (fieldName.equals(currentName)) {
        continue;
      }

      if (!GuiEditorUtil.isBindingUnique(component, fieldName, myEditor.getRootContainer())) {
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
        componentType = PsiManager.getInstance(myEditor.getProject()).getElementFactory().createTypeFromText(componentClassName, null);
      }
      catch (IncorrectOperationException e) {
        continue;
      }
      if (componentType == null) {
        continue;
      }

      final PsiType fieldType = field.getType();
      if (fieldType == null) {
        continue;
      }

      if (!fieldType.isAssignableFrom(componentType)) {
        continue;
      }

      result.add(fieldName);
    }

    final String[] names = result.toArray(new String[result.size()]);
    Arrays.sort(names);
    return names;
  }

  public Object getValue() throws Exception {
    final String value = (String)super.getValue();
    return value != null ? value.replace('$', '.') : null; // PSI works only with dots
  }

  public JComponent getComponent(final RadComponent component, final Object value, final boolean inplace){
    final String currentName = (String)value;
    final String[] fieldNames = getFieldNames(component, currentName);
    myCbx.setModel(new DefaultComboBoxModel(fieldNames));
    myCbx.setSelectedItem(value);
    return myCbx;
  }
}
