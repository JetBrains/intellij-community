package com.theoryinpractice.testng.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiNameValuePair;
import com.theoryinpractice.testng.TestNGDefaultConfigurationComponent;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class DependsOnGroupsInspection extends LocalInspectionTool
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
    private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z1-9_\\(\\)]*)\"");
    private static final ProblemDescriptor[] EMPTY = new ProblemDescriptor[0];

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
                if (param.getName().matches("(groups|dependsOnGroups)")) {
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
                        checkGroupNameDependency(manager, psiClass, methodName, dep, problemDescriptors);
                    }
                }
            }
        }

        return problemDescriptors.toArray(new ProblemDescriptor[] {});
    }

    private void checkGroupNameDependency(InspectionManager manager, PsiClass psiClass, String groupName, PsiNameValuePair dep, List<ProblemDescriptor> problemDescriptors) {

        TestNGDefaultConfigurationComponent defaultConfig = manager.getProject().getComponent(TestNGDefaultConfigurationComponent.class);
        List<String> groups = defaultConfig.getDefaultSettings().getGroups();

        if (!groups.contains(groupName)) {
            LOGGER.info("group doesn't exist:" + groupName);
            ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                                           "Group '" + groupName + "' is undefined.",
                                                                           new GroupNameQuickFix(manager.getProject(), groupName),
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            problemDescriptors.add(descriptor);

        }

    }

    private static class GroupNameQuickFix implements LocalQuickFix
    {

        Project project;
        String groupName;

        public GroupNameQuickFix(@NotNull Project project, @NotNull String groupName) {
            this.project = project;
            this.groupName = groupName;
        }

        @NotNull
        public String getName() {
            return "Add '" + groupName + "' as a defined test group.";
        }

        @NotNull
        public String getFamilyName() {
            return "TestNG";
        }

        public void applyFix(@NotNull Project project, ProblemDescriptor problemDescriptor) {
            TestNGDefaultConfigurationComponent defaultConfig = project.getComponent(TestNGDefaultConfigurationComponent.class);
            List<String> groups = defaultConfig.getDefaultSettings().getGroups();
            groups.add(groupName);
            try {
                defaultConfig.apply();
            } catch (ConfigurationException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}