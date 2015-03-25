/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.components.JBList;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.intellij.util.containers.ContainerUtilRt.addIfNotNull;

/**
 * User : ktisha
 */
public class PyPep8NamingInspection extends PyInspection {
  private static final Pattern LOWERCASE_REGEX = Pattern.compile("[_\\p{javaLowerCase}][_\\p{javaLowerCase}0-9]*");
  private static final Pattern UPPERCASE_REGEX = Pattern.compile("[_\\p{javaUpperCase}][_\\p{javaUpperCase}0-9]*");
  private static final Pattern MIXEDCASE_REGEX = Pattern.compile("_?[\\p{javaUpperCase}][\\p{javaLowerCase}\\p{javaUpperCase}0-9]*");

  public boolean ignoreOverriddenFunctions = true;
  public List<String> ignoredBaseClasses = Lists.newArrayList("unittest.TestCase", "unittest.case.TestCase");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, true, PyClass.class);
      if (function == null) return;
      final Scope scope = ControlFlowCache.getScope(function);
      for (PyExpression expression : node.getTargets()) {
        final String name = expression.getName();
        if (name == null || scope.isGlobal(name)) continue;
        if (expression instanceof PyTargetExpression) {
          final PyExpression qualifier = ((PyTargetExpression)expression).getQualifier();
          if (qualifier != null) {
            return;
          }
        }
        if (!LOWERCASE_REGEX.matcher(name).matches() && !name.startsWith("_")) {
          registerAndAddRenameQuickFix(expression, "Variable in function should be lowercase");
        }
      }
    }

    @Override
    public void visitPyParameter(PyParameter node) {
      final String name = node.getName();
      if (name == null) return;
      if (!LOWERCASE_REGEX.matcher(name).matches()) {
        registerAndAddRenameQuickFix(node, "Argument name should be lowercase");
      }
    }

    private void registerAndAddRenameQuickFix(@Nullable final PsiElement node, @NotNull final String name) {
      if (getHolder() != null && getHolder().isOnTheFly())
        registerProblem(node, name, new PyRenameElementQuickFix());
      else
        registerProblem(node, name);
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
          final List<LocalQuickFix> quickFixes = Lists.newArrayList();
          if (getHolder() != null && getHolder().isOnTheFly())
            quickFixes.add(new PyRenameElementQuickFix());

          if (containingClass != null) {
            quickFixes.add(new IgnoreBaseClassQuickFix(containingClass, myTypeEvalContext));
          }
          registerProblem(nameNode.getPsi(), "Function name should be lowercase", quickFixes.toArray(new LocalQuickFix[quickFixes.size()]));
        }
      }
    }

    private boolean isOverriddenMethod(@NotNull PyFunction function) {
      return PySuperMethodsSearch.search(function).findFirst() != null;
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
      if (!MIXEDCASE_REGEX.matcher(name).matches()) {
        final ASTNode nameNode = node.getNameNode();
        if (nameNode != null) {
          registerAndAddRenameQuickFix(nameNode.getPsi(), "Class names should use CamelCase convention");
        }
      }
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      final String asName = node.getAsName();
      final QualifiedName importedQName = node.getImportedQName();
      if (importedQName == null) return;
      final String name = importedQName.getLastComponent();

      if (asName == null || name == null) return;
      if (UPPERCASE_REGEX.matcher(name).matches()) {
        if (!UPPERCASE_REGEX.matcher(asName).matches()) {
          registerAndAddRenameQuickFix(node.getAsNameElement(), "Constant variable imported as non constant");
        }
      }
      else if (LOWERCASE_REGEX.matcher(name).matches()) {
        if (!LOWERCASE_REGEX.matcher(asName).matches()) {
          registerAndAddRenameQuickFix(node.getAsNameElement(), "Lowercase variable imported as non lowercase");
        }
      }
      else if (LOWERCASE_REGEX.matcher(asName).matches()) {
        registerAndAddRenameQuickFix(node.getAsNameElement(), "CamelCase variable imported as lowercase");
      }
      else if (UPPERCASE_REGEX.matcher(asName).matches()) {
        registerAndAddRenameQuickFix(node.getAsNameElement(), "CamelCase variable imported as constant");
      }
    }
  }

  private static class IgnoreBaseClassQuickFix implements LocalQuickFix {
    final List<String> myBaseClassNames;

    public IgnoreBaseClassQuickFix(@NotNull PyClass baseClass, @NotNull TypeEvalContext context) {
      myBaseClassNames = new ArrayList<String>();
      ContainerUtil.addIfNotNull(myBaseClassNames, baseClass.getQualifiedName());
      for (PyClass ancestor : baseClass.getAncestorClasses(context)) {
        ContainerUtil.addIfNotNull(myBaseClassNames, ancestor.getQualifiedName());
      }
    }

    @NotNull
    @Override
    public String getName() {
      return "Ignore method names for descendants of class";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final JBList list = new JBList(myBaseClassNames);
      final Runnable updateBlackList = new Runnable() {
        @Override
        public void run() {
          final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
          profile.modifyProfile(new Consumer<ModifiableModel>() {
            @Override
            public void consume(ModifiableModel model) {
              final PyPep8NamingInspection inspection = (PyPep8NamingInspection)model
                .getUnwrappedTool(PyPep8NamingInspection.class.getSimpleName(), descriptor.getPsiElement());
              addIfNotNull(inspection.ignoredBaseClasses, (String)list.getSelectedValue());
            }
          });
        }
      };
      DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
        @Override
        public void consume(DataContext dataContext) {
          new PopupChooserBuilder(list)
            .setTitle("Ignore base class")
            .setItemChoosenCallback(updateBlackList)
            .setFilteringEnabled(new Function<Object, String>() {
              @Override
              public String fun(Object o) {
                return (String)o;
              }
            })
            .createPopup()
            .showInBestPositionFor(dataContext);
        }
      });
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(new CheckBox("Ignore overridden functions", this, "ignoreOverriddenFunctions"), BorderLayout.NORTH);
    rootPanel.add(new ListEditForm("Excluded base classes", ignoredBaseClasses).getContentPanel(), BorderLayout.CENTER);
    return rootPanel;
  }
}
