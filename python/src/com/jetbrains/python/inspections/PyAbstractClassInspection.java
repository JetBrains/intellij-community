// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.inspections.quickfix.PyImplementMethodsQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PyAbstractClassInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.abstract.class");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    private Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(PyClass pyClass) {
      if (isAbstract(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
        return;
      }
      final List<PyFunction> toImplement = PyOverrideImplementUtil.getAllSuperAbstractMethods(pyClass, myTypeEvalContext);
      final ASTNode nameNode = pyClass.getNameNode();
      if (!toImplement.isEmpty() && nameNode != null) {
        final SmartList<LocalQuickFix> quickFixes = new SmartList<>(
          new PyImplementMethodsQuickFix(pyClass, toImplement),
          new SetABCMetaAsMetaclassQuickFix()
        );

        if (LanguageLevel.forElement(pyClass).isPy3K()) {
          quickFixes.add(new AddABCToSuperclassesQuickFix());
        }

        registerProblem(nameNode.getPsi(),
                        PyBundle.message("INSP.NAME.abstract.class.$0.must.implement", pyClass.getName()),
                        quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }

    private boolean isAbstract(@NotNull PyClass pyClass) {
      final PyClassLikeType metaClass = pyClass.getMetaClassType(false, myTypeEvalContext);
      if (metaClass != null && PyNames.ABC_META_CLASS.equals(metaClass.getName())) {
        return true;
      }
      for (PyClassLikeType superClassType : pyClass.getSuperClassTypes(myTypeEvalContext)) {
        if (superClassType != null && PyNames.ABC.equals(superClassType.getClassQName())) {
          return true;
        }
      }
      for (PyFunction method : pyClass.getMethods()) {
        if (PyKnownDecoratorUtil.hasAbstractDecorator(method, myTypeEvalContext)) {
          return true;
        }
      }
      return false;
    }

    private static class AddABCToSuperclassesQuickFix implements LocalQuickFix {

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return "Add '" + PyNames.ABC + "' to superclasses";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PyClass cls = PyUtil.as(descriptor.getPsiElement().getParent(), PyClass.class);
        if (cls == null) return;

        final PyClass abcClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC, cls);
        if (abcClass == null) return;

        PyClassRefactoringUtil.addSuperclasses(project, cls, abcClass);
      }
    }

    private static class SetABCMetaAsMetaclassQuickFix implements LocalQuickFix {

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return "Set '" + PyNames.ABC_META + "' as metaclass";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PyClass cls = PyUtil.as(descriptor.getPsiElement().getParent(), PyClass.class);
        if (cls == null) return;

        final PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, cls);
        if (abcMetaClass == null) return;

        final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
        PyClassRefactoringUtil.addMetaClassIfNotExist(cls, abcMetaClass, context);
      }
    }
  }
}
