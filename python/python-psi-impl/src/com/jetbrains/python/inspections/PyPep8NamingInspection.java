// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * User : ktisha
 */
public final class PyPep8NamingInspection extends PyInspection {

  protected static final String INSPECTION_SHORT_NAME = "PyPep8NamingInspection";
  private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
  private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
  private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");
  // See error codes of the tool "pep8-naming"
  private static final Map<String, Supplier<@InspectionMessage String>> ERROR_CODES_DESCRIPTION = Map.of(
    "N801", PyPsiBundle.messagePointer("INSP.pep8.naming.class.names.should.use.capwords.convention"),
    "N802", PyPsiBundle.messagePointer("INSP.pep8.naming.function.name.should.be.lowercase"),
    "N803", PyPsiBundle.messagePointer("INSP.pep8.naming.argument.name.should.be.lowercase"),
    "N806", PyPsiBundle.messagePointer("INSP.pep8.naming.variable.in.function.should.be.lowercase"),
    "N811", PyPsiBundle.messagePointer("INSP.pep8.naming.constant.variable.imported.as.non.constant"),
    "N812", PyPsiBundle.messagePointer("INSP.pep8.naming.lowercase.variable.imported.as.non.lowercase"),
    "N813", PyPsiBundle.messagePointer("INSP.pep8.naming.camelcase.variable.imported.as.lowercase"),
    "N814", PyPsiBundle.messagePointer("INSP.pep8.naming.camelcase.variable.imported.as.constant")
  );
  public final List<String> ignoredErrors = new ArrayList<>();
  public boolean ignoreOverriddenFunctions = true;
  public final List<String> ignoredBaseClasses = Lists.newArrayList("unittest.TestCase", "unittest.case.TestCase");

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreOverriddenFunctions", PyPsiBundle.message("ignore.overridden.functions")),
      horizontalStack(
        stringList("ignoredBaseClasses", PyPsiBundle.message("INSP.pep8.naming.column.name.excluded.base.classes")),
        stringList("ignoredErrors", PyPsiBundle.message("INSP.pep8.naming.column.name.ignored.errors"))
      )
    );
  }

  protected void addFunctionQuickFixes(ProblemsHolder holder,
                                       PyClass containingClass,
                                       ASTNode nameNode,
                                       List<LocalQuickFix> quickFixes, TypeEvalContext typeEvalContext) {
    if (holder != null && holder.isOnTheFly()) {
      LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(nameNode.getPsi());
      if (qf != null) {
        quickFixes.add(qf);
      }
    }

    if (containingClass != null) {
      quickFixes.add(new PyPep8NamingInspection.IgnoreBaseClassQuickFix(containingClass, typeEvalContext));
    }
  }

  protected LocalQuickFix[] createRenameAndIgnoreErrorQuickFixes(@Nullable PsiElement node,
                                                                 String errorCode) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (node != null) {
      LocalQuickFix qf = PythonUiService.getInstance().createPyRenameElementQuickFix(node);
      if (qf != null) {
        fixes.add(qf);
      }
      fixes.add(new IgnoreErrorFix(errorCode));
    }
    return fixes.toArray(new LocalQuickFix[fixes.size()]);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class IgnoreBaseClassQuickFix implements LocalQuickFix {
    private final List<String> myBaseClassNames;

    IgnoreBaseClassQuickFix(@NotNull PyClass baseClass, @NotNull TypeEvalContext context) {
      myBaseClassNames = new ArrayList<>();
      ContainerUtil.addIfNotNull(getBaseClassNames(), baseClass.getQualifiedName());
      for (PyClass ancestor : baseClass.getAncestorClasses(context)) {
        ContainerUtil.addIfNotNull(getBaseClassNames(), ancestor.getQualifiedName());
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PyPsiBundle.message("INSP.pep8.ignore.method.names.for.descendants.of.class");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      PythonUiService.getInstance().showPopup(project, getBaseClassNames(), PyPsiBundle.message("INSP.pep8.ignore.base.class"),
                                              (selectedValue) -> InspectionProfileModifiableModelKt
                                                .modifyAndCommitProjectProfile(project, it -> {
                                                  PyPep8NamingInspection inspection =
                                                    (PyPep8NamingInspection)it
                                                      .getUnwrappedTool(PyPep8NamingInspection.class.getSimpleName(),
                                                                        descriptor.getPsiElement());
                                                  ContainerUtil.addIfNotNull(inspection.ignoredBaseClasses, selectedValue);
                                                }));
    }

    public List<String> getBaseClassNames() {
      return myBaseClassNames;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      // The quick fix updates the inspection's settings, nothing changes in the current file
      return IntentionPreviewInfo.EMPTY;
    }
  }

  protected class IgnoreErrorFix implements LocalQuickFix {
    private final String myCode;

    IgnoreErrorFix(String code) {
      myCode = code;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PyPsiBundle.message("QFIX.NAME.ignore.errors.like.this");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      if (ignoredErrors.contains(myCode)) return IntentionPreviewInfo.EMPTY;
      ArrayList<String> updated = new ArrayList<>(ignoredErrors);
      updated.add(myCode);
      return IntentionPreviewInfo.addListOption(updated, myCode, PyPsiBundle.message("INSP.pep8.naming.column.name.ignored.errors"));
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiFile file = descriptor.getStartElement().getContainingFile();
      InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, model -> {
        PyPep8NamingInspection tool = (PyPep8NamingInspection)model.getUnwrappedTool(INSPECTION_SHORT_NAME, file);
        if (!tool.ignoredErrors.contains(myCode)) {
          tool.ignoredErrors.add(myCode);
        }
      });
    }
  }

  public class Visitor extends PyInspectionVisitor {
    public Visitor(ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
      if (function == null) return;
      final Scope scope = ControlFlowCache.getScope(function);
      for (Pair<PyExpression, PyExpression> pair : node.getTargetsToValuesMapping()) {
        final PyExpression value = pair.getFirst();
        if (value == null) continue;
        final String name = value.getName();
        if (name == null || scope.isGlobal(name)) continue;
        if (value instanceof PyTargetExpression) {
          final PyExpression qualifier = ((PyTargetExpression)value).getQualifier();
          if (qualifier != null) {
            return;
          }
        }

        final PyCallExpression assignedValue = PyUtil.as(pair.getSecond(), PyCallExpression.class);
        if (assignedValue != null
            && assignedValue.getCallee() != null && PyNames.NAMEDTUPLE.equals(assignedValue.getCallee().getName())) {
          return;
        }
        final String errorCode = "N806";
        if (!LOWERCASE_REGEX.matcher(name).matches() && !name.startsWith("_") && !ignoredErrors.contains(errorCode)) {
          registerAndAddRenameAndIgnoreErrorQuickFixes(value, errorCode);
        }
      }
    }

    @Override
    public void visitPyParameter(@NotNull PyParameter node) {
      final String name = node.getName();
      if (name == null) return;

      final String errorCode = "N803";
      if (!LOWERCASE_REGEX.matcher(name).matches() && !ignoredErrors.contains(errorCode)) {
        registerAndAddRenameAndIgnoreErrorQuickFixes(node, errorCode);
      }
    }

    protected void registerAndAddRenameAndIgnoreErrorQuickFixes(@Nullable final PsiElement node, @NotNull final String errorCode) {
      if (getHolder() != null && getHolder().isOnTheFly()) {
        registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode).get(), createRenameAndIgnoreErrorQuickFixes(node, errorCode));
      }
      else {
        registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode).get(), new IgnoreErrorFix(errorCode));
      }
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction function) {
      final PyClass containingClass = function.getContainingClass();
      if (ignoreOverriddenFunctions && isOverriddenMethod(function)) return;
      final String name = function.getName();
      if (name == null) return;
      if (containingClass != null && (PyUtil.isSpecialName(name) || isIgnoredOrHasIgnoredAncestor(containingClass))) {
        return;
      }
      if (!LOWERCASE_REGEX.matcher(name).matches()) {
        final ASTNode nameNode = function.getNameNode();
        if (nameNode != null) {
          final List<LocalQuickFix> quickFixes = new ArrayList<>();
          addFunctionQuickFixes(getHolder(), containingClass, nameNode, quickFixes, myTypeEvalContext);
          final String errorCode = "N802";
          if (!ignoredErrors.contains(errorCode)) {
            quickFixes.add(new IgnoreErrorFix(errorCode));
            registerProblem(nameNode.getPsi(), ERROR_CODES_DESCRIPTION.get(errorCode).get(),
                            quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }

    private boolean isOverriddenMethod(@NotNull PyFunction function) {
      return PySuperMethodsSearch.search(function, myTypeEvalContext).findFirst() != null;
    }

    private boolean isIgnoredOrHasIgnoredAncestor(@NotNull PyClass pyClass) {
      final Set<String> blackList = Sets.newHashSet(ignoredBaseClasses);
      if (blackList.contains(pyClass.getQualifiedName())) {
        return true;
      }
      for (PyClassLikeType ancestor : pyClass.getAncestorTypes(myTypeEvalContext)) {
        if (ancestor != null && blackList.contains(ancestor.getClassQName())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      final String name = node.getName();
      if (name == null) return;
      final String errorCode = "N801";
      if (!ignoredErrors.contains(errorCode)) {
        final boolean isLowercaseContextManagerClass = isContextManager(node) && LOWERCASE_REGEX.matcher(name).matches();
        if (!isLowercaseContextManagerClass && !MIXEDCASE_REGEX.matcher(name).matches()) {
          final ASTNode nameNode = node.getNameNode();
          if (nameNode != null) {
            registerAndAddRenameAndIgnoreErrorQuickFixes(nameNode.getPsi(), errorCode);
          }
        }
      }
    }

    private boolean isContextManager(PyClass node) {
      final String[] contextManagerFunctionNames = {PyNames.ENTER, PyNames.EXIT};
      for (String name : contextManagerFunctionNames) {
        if (node.findMethodByName(name, false, myTypeEvalContext) == null) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void visitPyImportElement(@NotNull PyImportElement node) {
      final String asName = node.getAsName();
      final QualifiedName importedQName = node.getImportedQName();
      if (importedQName == null) return;
      final String name = importedQName.getLastComponent();

      if (asName == null || name == null) return;
      if (UPPERCASE_REGEX.matcher(name).matches()) {
        final String errorCode = "N811";
        if (!UPPERCASE_REGEX.matcher(asName).matches() && !ignoredErrors.contains(errorCode)) {
          registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
        }
      }
      else if (LOWERCASE_REGEX.matcher(name).matches()) {
        final String errorCode = "N812";
        if (!LOWERCASE_REGEX.matcher(asName).matches() && !ignoredErrors.contains(errorCode)) {
          registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
        }
      }
      else if (LOWERCASE_REGEX.matcher(asName).matches()) {
        final String errorCode = "N813";
        if (!ignoredErrors.contains(errorCode)) {
          registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
        }
      }
      else if (UPPERCASE_REGEX.matcher(asName).matches()) {
        final String errorCode = "N814";
        if (!ignoredErrors.contains(errorCode)) {
          registerAndAddRenameAndIgnoreErrorQuickFixes(node.getAsNameElement(), errorCode);
        }
      }
    }
  }
}
