/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MakeMethodStaticRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeMethodStatic.Settings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class MakeMethodStaticRefactoringImpl extends RefactoringImpl<MakeMethodStaticProcessor> implements MakeMethodStaticRefactoring {
  MakeMethodStaticRefactoringImpl(Project project,
                                  PsiMethod method,
                                  boolean replaceUsages,
                                  String classParameterName,
                                  PsiField[] fields,
                                  String[] names) {
    super(new MakeMethodStaticProcessor(project, method, true,
                                        new Settings(replaceUsages, classParameterName, fields, names),
                                        BaseRefactoringProcessor.EMPTY_CALLBACK));
  }

  public PsiMethod getMethod() {
    return myProcessor.getMethod();
  }

  public boolean isReplaceUsages() {
    return myProcessor.getSettings().isReplaceUsages();
  }

  public String getClassParameterName() {
    return myProcessor.getSettings().getClassParameterName();
  }

  public List<PsiField> getFields() {
    final Settings settings = myProcessor.getSettings();
    List<PsiField> result = new ArrayList<PsiField>();
    final List<Settings.FieldParameter> parameterOrderList = settings.getParameterOrderList();
    for (Iterator<Settings.FieldParameter> iterator = parameterOrderList.iterator(); iterator.hasNext();) {
      final Settings.FieldParameter fieldParameter = iterator.next();
      result.add(fieldParameter.field);
    }

    return result;
  }

  public String getParameterNameForField(PsiField field) {
    return myProcessor.getSettings().getNameForField(field);
  }


}
