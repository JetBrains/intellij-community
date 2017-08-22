/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependsOnGroupsInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z0-9_\\-\\(\\)]*)\"");

  public JDOMExternalizableStringList groups = new JDOMExternalizableStringList();
  @NonNls public static final String SHORT_NAME = "groupsTestNG";

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "TestNG";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Groups problem";
  }

  @NotNull
  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final LabeledComponent<JTextField> definedGroups = new LabeledComponent<>();
    definedGroups.setText("&Defined Groups");
    final JTextField textField = new JTextField(StringUtil.join(ArrayUtil.toStringArray(groups), ","));
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final DocumentEvent e) {
        groups.clear();
        String text = textField.getText();
        if (!StringUtil.isEmptyOrSpaces(text)) {
          ContainerUtil.addAll(groups, text.split("[, ]"));
        }
      }
    });
    definedGroups.setComponent(textField);
    final JPanel optionsPanel = new JPanel(new BorderLayout());
    optionsPanel.add(definedGroups, BorderLayout.NORTH);
    return optionsPanel;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {

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
              ProblemDescriptor descriptor = manager.createProblemDescriptor(annotation, "Group '" + methodName + "' is undefined.",
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

    public GroupNameQuickFix(@NotNull String groupName) {
      myGroupName = groupName;
    }

    @Override
    @NotNull
    public String getName() {
      return "Add '" + myGroupName + "' as a defined test group.";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return "TestNG";
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
