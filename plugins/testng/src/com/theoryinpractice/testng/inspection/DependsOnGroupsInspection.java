// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependsOnGroupsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z0-9_\\-\\(\\)]*)\"");

  public JDOMExternalizableStringList groups = new JDOMExternalizableStringList();
  public static final @NonNls String SHORT_NAME = "groupsTestNG";

  @Override
  public @NotNull String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.string("groups", TestngBundle.message("inspection.depends.on.groups.defined.groups.panel.title"),
                     30)
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController().onValue(
      "groups",
      () -> StringUtil.join(ArrayUtilRt.toStringArray(groups), ","),
      value -> {
        groups.clear();
        if (!StringUtil.isEmptyOrSpaces(value)) {
          ContainerUtil.addAll(groups, value.split("[, ]"));
        }
      });
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {

    if (!psiClass.getContainingFile().isWritable()) return null;

    PsiAnnotation[] annotations = TestNGUtil.getTestNGAnnotations(psiClass);
    if (annotations.length == 0) return ProblemDescriptor.EMPTY_ARRAY;

    List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
    for (PsiAnnotation annotation : annotations) {

      PsiNameValuePair dep = null;
      PsiNameValuePair[] params = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair param : params) {
        if (param.getName() != null && param.getName().matches("(groups|dependsOnGroups)")) {
          dep = param;
          break;
        }
      }

      if (dep != null) {
        final PsiAnnotationMemberValue value = dep.getValue();
        if (value != null) {
          LOGGER.debug("Found " + dep.getName() + " with: " + value.getText());
          String text = value.getText();
          if (value instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)value).resolve();
            if (resolve instanceof PsiField &&
                ((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC) &&
                ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
              final PsiExpression initializer = ((PsiField)resolve).getInitializer();
              if (initializer != null) {
                text = initializer.getText();
              }
            }
          }
          Matcher matcher = PATTERN.matcher(text);
          while (matcher.find()) {
            String methodName = matcher.group(1);
            if (!groups.contains(methodName)) {
              LOGGER.debug("group doesn't exist:" + methodName);
              ProblemDescriptor descriptor = manager.createProblemDescriptor(annotation, TestngBundle.message("inspection.depends.on.groups.undefined.group.problem", methodName),
                                                                             new GroupNameQuickFix(methodName),
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
              problemDescriptors.add(descriptor);

            }
          }
        }
      }
    }
    return problemDescriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private class GroupNameQuickFix implements LocalQuickFix {

    String myGroupName;

    GroupNameQuickFix(@NotNull String groupName) {
      myGroupName = groupName;
    }

    @Override
    public @NotNull String getName() {
      return TestngBundle.message("inspection.depends.on.groups.add.as.defined.test.group.fix", myGroupName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return TestngBundle.message("inspection.depends.on.groups.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
      groups.add(myGroupName);
      //correct save settings
      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
      //TODO lesya
      /*
      try {
        inspectionProfile.save();
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
      }
      */
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
