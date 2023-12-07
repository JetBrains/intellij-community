// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.properties.BindingProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadErrorComponent;
import com.intellij.uiDesigner.radComponents.RadHSpacer;
import com.intellij.uiDesigner.radComponents.RadVSpacer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager.getInstance;

public final class BindingEditor extends ComboBoxPropertyEditor<String> {

  public BindingEditor(final Project project) {
    myCbx.setEditable(true);
    final JComponent editorComponent = (JComponent)myCbx.getEditor().getEditorComponent();
    editorComponent.setBorder(null);

    myCbx.addActionListener(
      new ActionListener(){
        @Override
        public void actionPerformed(final ActionEvent e){
          fireValueCommitted(true, false);
        }
      }
    );

    new AnAction(){
      @Override
      public void actionPerformed(final @NotNull AnActionEvent e) {
        if (!myCbx.isPopupVisible()) {
          fireEditingCancelled();
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance()
            .requestFocus(getInstance(getInstance(project).getActiveFormEditor())
                            .getPropertyInspector(), true));
        }
      }
    }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, myCbx);
  }

  private static String[] getFieldNames(final RadComponent component, final String currentName) {
    final ArrayList<String> result = new ArrayList<>();
    if (currentName != null){
      result.add(currentName);
    }

    final IRootContainer root = FormEditingUtil.getRoot(component);
    final String className = root.getClassToBind();
    if (className == null) {
      return ArrayUtilRt.toStringArray(result);
    }

    final PsiClass aClass = FormEditingUtil.findClassToBind(component.getModule(), className);
    if (aClass == null) {
      return ArrayUtilRt.toStringArray(result);
    }

    final PsiField[] fields = aClass.getFields();

    for (final PsiField field : fields) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }

      final String fieldName = field.getName();

      if (Objects.equals(currentName, fieldName)) {
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
          JavaPsiFacade.getInstance(component.getProject()).getElementFactory().createTypeFromText(componentClassName, null);
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

    final String[] names = ArrayUtilRt.toStringArray(result);
    Arrays.sort(names);
    return names;
  }

  @Override
  public String getValue() throws Exception {
    final String value = super.getValue();
    return value != null ? value.replace('$', '.') : null; // PSI works only with dots
  }

  @Override
  public JComponent getComponent(final RadComponent component, final @NlsSafe String value, final InplaceContext inplaceContext){
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-653866")) {
      final String[] fieldNames = getFieldNames(component, value);
      myCbx.setModel(new DefaultComboBoxModel(fieldNames));
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }
}
