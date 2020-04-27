// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.CheckBox;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.PyRenameElementQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
public class PyPep8NamingInspection extends PyPsiPep8NamingInspection {

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.add(new CheckBox(PyBundle.message("ignore.overridden.functions"), this, "ignoreOverriddenFunctions"), BorderLayout.NORTH);

    final OnePixelSplitter splitter = new OnePixelSplitter(false);
    splitter.setFirstComponent(new ListEditForm("Excluded base classes", ignoredBaseClasses).getContentPanel());
    splitter.setSecondComponent(new ListEditForm("Ignored errors", ignoredErrors).getContentPanel());
    rootPanel.add(splitter, BorderLayout.CENTER);

    return rootPanel;
  }

  @Override
  protected void addFunctionQuickFixes(ProblemsHolder holder,
                                       PyClass containingClass,
                                       ASTNode nameNode,
                                       List<LocalQuickFix> quickFixes, TypeEvalContext typeEvalContext) {
    if (holder != null && holder.isOnTheFly()) {
      quickFixes.add(new PyRenameElementQuickFix(nameNode.getPsi()));
    }

    if (containingClass != null) {
      quickFixes.add(new PyPep8NamingInspection.IgnoreBaseClassQuickFix(containingClass, typeEvalContext));
    }
  }

  protected LocalQuickFix[] createRenameAndIngoreErrorQuickFixes(@Nullable PsiElement node,
                                                                 String errorCode) {
    return new LocalQuickFix[]{new PyRenameElementQuickFix(node), new IgnoreErrorFix(errorCode)};
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
      return PyBundle.message("INSP.pep8.ignore.method.names.for.descendants.of.class");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      DataManager.getInstance().getDataContextFromFocus().doWhenDone((Consumer<DataContext>)dataContext ->
        JBPopupFactory.getInstance().createPopupChooserBuilder(getBaseClassNames())
        .setTitle(PyBundle.message("INSP.pep8.ignore.base.class"))
        .setItemChosenCallback((selectedValue) -> InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
          PyPep8NamingInspection inspection =
            (PyPep8NamingInspection)it.getUnwrappedTool(PyPep8NamingInspection.class.getSimpleName(), descriptor.getPsiElement());
          ContainerUtil.addIfNotNull(inspection.ignoredBaseClasses, selectedValue);
        }))
        .setNamerForFiltering(o -> o)
        .createPopup()
        .showInBestPositionFor(dataContext));
    }

    public List<String> getBaseClassNames() {
      return myBaseClassNames;
    }
  }
}
