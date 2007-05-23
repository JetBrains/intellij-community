package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.ui.DocumentAdapter;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class DependsOnGroupsInspection extends LocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z1-9_\\(\\)]*)\"");
  private static final ProblemDescriptor[] EMPTY = new ProblemDescriptor[0];

  public JDOMExternalizableStringList groups = new JDOMExternalizableStringList();

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
    return "groupsTestNG";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final LabeledComponent<JTextField> definedGroups = new LabeledComponent<JTextField>();
    definedGroups.setText("&Defined Groups");
    final JTextField textField = new JTextField(StringUtil.join(groups.toArray(new String[groups.size()]), ","));
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        groups.clear();
        final String[] groupsFromString = textField.getText().split("[, ]");
        groups.addAll(Arrays.asList(groupsFromString));
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
              ProblemDescriptor descriptor = manager.createProblemDescriptor(dep, "Group '" + methodName + "' is undefined.",
                                                                             new GroupNameQuickFix(methodName),
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
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
    }
  }
}