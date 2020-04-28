// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User : ktisha
 */
public class PyPep8NamingInspection extends PyInspection {

  protected static final String INSPECTION_SHORT_NAME = "PyPep8NamingInspection";
  private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
  private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
  private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");
  // See error codes of the tool "pep8-naming"
  private static final Map<String, String> ERROR_CODES_DESCRIPTION = ImmutableMap.<String, String>builder()
    .put("N801", "Class names should use CamelCase convention")
    .put("N802", "Function name should be lowercase")
    .put("N803", "Argument name should be lowercase")
    .put("N806", "Variable in function should be lowercase")
    .put("N811", "Constant variable imported as non constant")
    .put("N812", "Lowercase variable imported as non lowercase")
    .put("N813", "CamelCase variable imported as lowercase")
    .put("N814", "CamelCase variable imported as constant")
    .build();
  public final List<String> ignoredErrors = new ArrayList<>();
  public boolean ignoreOverriddenFunctions = true;
  public final List<String> ignoredBaseClasses = Lists.newArrayList("unittest.TestCase", "unittest.case.TestCase");

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel rootPanel = new JPanel(new BorderLayout());
    JCheckBox checkBox = PythonUiService.getInstance()
      .createInspectionCheckBox(PyPsiBundle.message("ignore.overridden.functions"), this, "ignoreOverriddenFunctions");
    if (checkBox != null) {
      rootPanel.add(checkBox, BorderLayout.NORTH);
    }

    JComponent classes = PythonUiService.getInstance().createListEditForm("Excluded base classes", ignoredBaseClasses);
    JComponent errors = PythonUiService.getInstance().createListEditForm("Ignored errors", ignoredErrors);

    if (classes != null && errors != null) {
      JComponent splitter = PythonUiService.getInstance().onePixelSplitter(false, classes, errors);

      if (splitter != null) {
        rootPanel.add(splitter, BorderLayout.CENTER);
      }
    }

    return rootPanel;
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

  protected LocalQuickFix[] createRenameAndIngoreErrorQuickFixes(@Nullable PsiElement node,
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
    return new Visitor(holder, session);
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
  }

  protected static class IgnoreErrorFix implements LocalQuickFix {
    private final String myCode;
    private static final String myText = "Ignore errors like this";

    IgnoreErrorFix(String code) {
      myCode = code;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myText;
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
    public Visitor(ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
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
    public void visitPyParameter(PyParameter node) {
      final String name = node.getName();
      if (name == null) return;

      final String errorCode = "N803";
      if (!LOWERCASE_REGEX.matcher(name).matches() && !ignoredErrors.contains(errorCode)) {
        registerAndAddRenameAndIgnoreErrorQuickFixes(node, errorCode);
      }
    }

    protected void registerAndAddRenameAndIgnoreErrorQuickFixes(@Nullable final PsiElement node, @NotNull final String errorCode) {
      if (getHolder() != null && getHolder().isOnTheFly()) {
        registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode), createRenameAndIngoreErrorQuickFixes(node, errorCode));
      }
      else {
        registerProblem(node, ERROR_CODES_DESCRIPTION.get(errorCode), new IgnoreErrorFix(errorCode));
      }
    }

    @Override
    public void visitPyFunction(PyFunction function) {
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
            registerProblem(nameNode.getPsi(), ERROR_CODES_DESCRIPTION.get(errorCode),
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
    public void visitPyClass(PyClass node) {
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
    public void visitPyImportElement(PyImportElement node) {
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
