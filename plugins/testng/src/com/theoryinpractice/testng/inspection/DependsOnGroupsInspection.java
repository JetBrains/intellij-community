/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameValuePair;
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

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class DependsOnGroupsInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z1-9_\\(\\)]*)\"");
  private static final ProblemDescriptor[] EMPTY = new ProblemDescriptor[0];

  public JDOMExternalizableStringList groups = new JDOMExternalizableStringList();
  @NonNls public static String SHORT_NAME = "groupsTestNG";

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

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final LabeledComponent<JTextField> definedGroups = new LabeledComponent<JTextField>();
    definedGroups.setText("&Defined Groups");
    final JTextField textField = new JTextField(StringUtil.join(ArrayUtil.toStringArray(groups), ","));
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        groups.clear();
        final String[] groupsFromString = textField.getText().split("[, ]");
        ContainerUtil.addAll(groups, groupsFromString);
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
    if (annotations.length == 0) return EMPTY;

    List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();
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
        if (dep.getValue() != null) {
          LOGGER.info("Found " + dep.getName() + " with: " + dep.getValue().getText());
          Matcher matcher = PATTERN.matcher(dep.getValue().getText());
          while (matcher.find()) {
            String methodName = matcher.group(1);
            if (!groups.contains(methodName)) {
              LOGGER.info("group doesn't exist:" + methodName);
              ProblemDescriptor descriptor = manager.createProblemDescriptor(annotation, "Group '" + methodName + "' is undefined.",
                                                                             new GroupNameQuickFix(methodName),
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly);
              problemDescriptors.add(descriptor);

            }
          }
        }
      }
    }
    return problemDescriptors.toArray(new ProblemDescriptor[]{});
  }

  private class GroupNameQuickFix implements LocalQuickFix {

    String myGroupName;

    public GroupNameQuickFix(@NotNull String groupName) {
      myGroupName = groupName;
    }

    @NotNull
    public String getName() {
      return "Add '" + myGroupName + "' as a defined test group.";
    }

    @NotNull
    public String getFamilyName() {
      return "TestNG";
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor problemDescriptor) {
      groups.add(myGroupName);
      final InspectionProfile inspectionProfile =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      //correct save settings
      ((InspectionProfileImpl)inspectionProfile).isProperSetting(HighlightDisplayKey.find(SHORT_NAME));
      InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
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
  }
}
