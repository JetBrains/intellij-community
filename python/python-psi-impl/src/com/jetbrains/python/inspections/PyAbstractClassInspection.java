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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyAbstractClassInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static final class Visitor extends PyInspectionVisitor {

    private Visitor(@NotNull ProblemsHolder holder,
                    @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyClass(@NotNull PyClass pyClass) {
      if (isAbstract(pyClass) || PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
        return;
      }

      /* Do not report problem if class contains only methods that raise NotImplementedError without any abc.* decorators
         but keep ability to implement them via quickfix (see PY-38680) */
      final List<PyFunction> toImplement = ContainerUtil
        .filter(PyPsiRefactoringUtil.getAllSuperAbstractMethods(pyClass, myTypeEvalContext),
                function -> PyKnownDecoratorUtil.hasAbstractDecorator(function, myTypeEvalContext));

      final ASTNode nameNode = pyClass.getNameNode();
      if (!toImplement.isEmpty() && nameNode != null) {
        final SmartList<LocalQuickFix> quickFixes = new SmartList<>();

        LocalQuickFix qf = PythonUiService.getInstance().createPyImplementMethodsQuickFix(pyClass, toImplement);
        if (qf != null) {
          quickFixes.add(qf);
        }
        quickFixes.add(new SetABCMetaAsMetaclassQuickFix());

        if (LanguageLevel.forElement(pyClass).isPy3K()) {
          quickFixes.add(new AddABCToSuperclassesQuickFix());
        }

        registerProblem(nameNode.getPsi(),
                        PyPsiBundle.message("INSP.abstract.class.class.must.implement.all.abstract.methods", pyClass.getName()),
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
        return PyPsiBundle.message("INSP.abstract.class.add.to.superclasses", PyNames.ABC);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PyClass cls = PyUtil.as(descriptor.getPsiElement().getParent(), PyClass.class);
        if (cls == null) return;

        final PyClass abcClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC, cls);
        if (abcClass == null) return;

        PyPsiRefactoringUtil.addSuperclasses(project, cls, abcClass);
      }
    }

    private static class SetABCMetaAsMetaclassQuickFix implements LocalQuickFix {

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return PyPsiBundle.message("INSP.abstract.class.set.as.metaclass", PyNames.ABC_META);
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PyClass cls = PyUtil.as(descriptor.getPsiElement().getParent(), PyClass.class);
        if (cls == null) return;

        final PyClass abcMetaClass = PyPsiFacade.getInstance(project).createClassByQName(PyNames.ABC_META, cls);
        if (abcMetaClass == null) return;

        final TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
        PyPsiRefactoringUtil.addMetaClassIfNotExist(cls, abcMetaClass, context);
      }
    }
  }
}
