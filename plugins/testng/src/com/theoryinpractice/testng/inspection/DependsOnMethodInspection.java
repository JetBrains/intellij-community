package com.theoryinpractice.testng.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class DependsOnMethodInspection extends LocalInspectionTool
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");

    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "TestNG";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "dependsOnMethods problem";
    }

    @NotNull
    @Override
    public String getShortName() {
        return "dependsOnMethodTestNG";
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {

        LOGGER.info("Looking for dependsOnMethods problems in " + psiClass.getName());

        if (!psiClass.getContainingFile().isWritable()) return null;

        List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();

        for (PsiAnnotation annotation : TestNGUtil.getTestNGAnnotations(psiClass)) {

            PsiNameValuePair dep = null;
            PsiNameValuePair[] params = annotation.getParameterList().getAttributes();
            for (PsiNameValuePair param : params) {
                if ("dependsOnMethods".equals(param.getName())) {
                    dep = param;
                    break;
                }
            }

            if (dep != null) {
                Matcher matcher = Pattern.compile("\"([a-zA-Z1-9_\\(\\)]*)\"").matcher(dep.getValue().getText());
                while (matcher.find()) {
                    String methodName = matcher.group(1);
                    checkMethodNameDependency(manager, psiClass, methodName, dep, problemDescriptors);
                }
            }
        }
        
        return problemDescriptors.toArray(new ProblemDescriptor[] {} );
    }

    private void checkMethodNameDependency(InspectionManager manager, PsiClass psiClass, String methodName, PsiNameValuePair dep, List<ProblemDescriptor> problemDescriptors) {
        LOGGER.info("Found dependsOnMethods with text: " + methodName);
        if (methodName.endsWith("()")) {

            LOGGER.info("dependsOnMethods contains ()" + psiClass.getName());
            // TODO Add quick fix for removing brackets on annotation
            ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                               "Method '" + methodName + "' should not include () characters.",
                                                               (LocalQuickFix) null,
                                                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

            problemDescriptors.add(descriptor);

        } else {
            boolean methodExists = false;
            PsiMethod[] methods = psiClass.getMethods();
            for (PsiMethod method : methods) {
                LOGGER.info("Checking method with name: " + method.getName());
                if (method.getName().equals(methodName)) {
                    methodExists = true;
                    break;
                }
            }

            if (!methodExists) {
                LOGGER.info("dependsOnMethods method doesn't exist:" + methodName);
                ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                                   "Method '" + methodName + "' unknown.",
                                                                   (LocalQuickFix) null,
                                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                problemDescriptors.add(descriptor);

            }
        }
    }
}