package com.theoryinpractice.testng.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        //LOGGER.info("Looking for dependsOnMethods problems in " + psiClass.getName());

        if (!psiClass.getContainingFile().isWritable()) return null;

        PsiAnnotation[] annotations = TestNGUtil.getTestNGAnnotations(psiClass);
        if(annotations.length == 0) return EMPTY;
        List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();

        for (PsiAnnotation annotation : annotations) {
            PsiNameValuePair dep = null;
            PsiNameValuePair[] params = annotation.getParameterList().getAttributes();
            for (PsiNameValuePair param : params) {
                if ("dependsOnMethods".equals(param.getName())) {
                    dep = param;
                    break;
                }
            }

            if (dep != null) {
                if (dep.getValue() != null) {
                    Matcher matcher = PATTERN.matcher(dep.getValue().getText());
                    while (matcher.find()) {
                        String methodName = matcher.group(1);
                        checkMethodNameDependency(manager, psiClass, methodName, dep, problemDescriptors);
                    }
                }
            }
        }
        
        return problemDescriptors.toArray(new ProblemDescriptor[] {} );
    }

    private void checkMethodNameDependency(InspectionManager manager, PsiClass psiClass, String methodName, PsiNameValuePair dep, List<ProblemDescriptor> problemDescriptors) {
        LOGGER.debug("Found dependsOnMethods with text: " + methodName);
        if (methodName.length() > 0 && methodName.charAt(methodName.length() - 1) == ')') {

            LOGGER.debug("dependsOnMethods contains ()" + psiClass.getName());
            // TODO Add quick fix for removing brackets on annotation
            ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                               "Method '" + methodName + "' should not include () characters.",
                                                               (LocalQuickFix) null,
                                                               ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

            problemDescriptors.add(descriptor);

        } else {
            boolean methodExists = false;
            PsiMethod[] methods = psiClass.getMethods();
            PsiMethod foundMethod = null;
            for (PsiMethod method : methods) {
                LOGGER.debug("Checking method with name: " + method.getName());
                if (method.getName().equals(methodName)) {
                    methodExists = true;
                    break;
                }
            }

            if (!methodExists) {
                LOGGER.debug("dependsOnMethods method doesn't exist:" + methodName);
                ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                                   "Method '" + methodName + "' unknown.",
                                                                   (LocalQuickFix) null,
                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                problemDescriptors.add(descriptor);

            } else if(foundMethod != null && !TestNGUtil.hasTest(foundMethod) && !!TestNGUtil.hasConfig(foundMethod)) {
                ProblemDescriptor descriptor = manager.createProblemDescriptor(dep,
                                                                   "Method '" + methodName + "' is not a test or configuration method.",
                                                                   (LocalQuickFix) null,
                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                problemDescriptors.add(descriptor);
            }
        }
    }

}