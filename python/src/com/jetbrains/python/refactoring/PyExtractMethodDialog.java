// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.google.common.base.Predicates;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.extractMethod.AbstractExtractMethodDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractMethod.ExtractMethodValidator;
import com.intellij.refactoring.util.AbstractParameterTablePanel.NameColumnInfo;
import com.intellij.refactoring.util.AbstractParameterTablePanel.PassParameterColumnInfo;
import com.intellij.util.ui.ColumnInfo;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodSettings;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodUtil;
import com.jetbrains.python.refactoring.extractmethod.PyVariableData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Predicate;

public class PyExtractMethodDialog extends AbstractExtractMethodDialog<Object> {
  private static final String TYPES_MESSAGE_KEY = "refactoring.extract.method.title.type";
  private static final String ADD_TYPE_ANNOTATIONS_MESSAGE_KEY = "refactoring.extract.method.addTypeAnnotations";

  private JCheckBox myAddTypeAnnotationsCheckbox;

  public PyExtractMethodDialog(Project project,
                               String defaultName,
                               PyCodeFragment fragment,
                               Object[] visibilityVariants,
                               ExtractMethodValidator validator,
                               ExtractMethodDecorator<Object> decorator,
                               FileType type) {
    super(project, defaultName, fragment, visibilityVariants, validator, decorator, type);
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent original = super.createCenterPanel();
    assert original != null;

    JPanel wrapper = new JPanel(new BorderLayout(0, 6));
    wrapper.add(original, BorderLayout.CENTER);
    boolean selected = PyExtractMethodUtil.getAddTypeAnnotations(myProject);
    myAddTypeAnnotationsCheckbox = new JCheckBox(PyBundle.message(ADD_TYPE_ANNOTATIONS_MESSAGE_KEY), selected);
    wrapper.add(myAddTypeAnnotationsCheckbox, BorderLayout.SOUTH);
    myAddTypeAnnotationsCheckbox.addChangeListener((changeEvent) -> updateSignature());
    return wrapper;
  }

  @Override
  protected void doOKAction() {
    PyExtractMethodUtil.setAddTypeAnnotations(myProject, myAddTypeAnnotationsCheckbox.isSelected());
    super.doOKAction();
  }

  @NotNull
  public PyExtractMethodSettings getExtractMethodSettings() {
    return new PyExtractMethodSettings(getMethodName(), getAbstractVariableData(), ((PyCodeFragment)myFragment).getOutputType(),
                                       myAddTypeAnnotationsCheckbox.isSelected());
  }

  @Override
  @NotNull
  protected PyVariableData @NotNull [] innerCreateVariableDataByNames(final @NotNull List<String> args) {
    final PyVariableData[] datas = new PyVariableData[args.size()];
    for (int i = 0; i < args.size(); i++) {
      final PyVariableData data = new PyVariableData();
      final String name = args.get(i);
      data.originalName = name;
      data.name = name;
      data.passAsParameter = true;
      data.typeName = ((PyCodeFragment)myFragment).getInputTypes().get(name);
      datas[i] = data;
    }
    return datas;
  }

  @Override
  protected @NotNull ColumnInfo @NotNull [] getParameterColumns() {
    return new ColumnInfo[]{new PassParameterColumnInfo(), new NameColumnInfo(myValidator::isValidName), new TypeColumnInfo()};
  }

  @Override
  protected void updateSignature() {
    mySignaturePreviewTextArea.setSignature(myDecorator.createMethodSignature(getExtractMethodSettings()));
  }

  @Override
  public @NotNull PyVariableData @NotNull [] getAbstractVariableData() {
    return (PyVariableData[])myVariableData;
  }

  public static class TypeColumnInfo extends ColumnInfo<PyVariableData, String> {
    private final Predicate<? super String> myNameValidator = Predicates.alwaysTrue();

    public TypeColumnInfo() {
      super(PyBundle.message(TYPES_MESSAGE_KEY));
    }

    @Override
    public @Nullable String valueOf(@NotNull PyVariableData data) {
      return data.getTypeName();
    }

    @Override
    public void setValue(@NotNull PyVariableData data, @NotNull String value) {
      if (myNameValidator.test(value)) {
        data.typeName = value;
      }
    }

    @Override
    public boolean isCellEditable(@NotNull PyVariableData data) {
      return true;
    }
  }
}
