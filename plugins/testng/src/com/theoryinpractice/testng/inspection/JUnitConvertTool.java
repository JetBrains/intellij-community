package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiClass;
import com.theoryinpractice.testng.util.Intentions;
import org.jetbrains.annotations.Nullable;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class JUnitConvertTool extends LocalInspectionTool
{
    @Override
    public String getGroupDisplayName() {
        return "TestNG";
    }

    @Override
    public String getDisplayName() {
        return "Convert JUnit Tests to TestNG";
    }

    @Override
    public String getShortName() {
        return "JUnitTestNG";
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkClass(PsiClass psiClass, InspectionManager manager, boolean isOnTheFly) {
        if (isOnTheFly) return null;
        if (!psiClass.getContainingFile().isWritable()) return null;
        boolean isJUnit = Intentions.inheritsJUnitTestCase(psiClass) || Intentions.containsJunitAnnotions(psiClass);
        if (!isJUnit) return null;
        ProblemDescriptor descriptor = manager.createProblemDescriptor(psiClass,
                                                                       "TestCase can be converted to TestNG",
                                                                       new JUnitConverterQuickFix(),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        return new ProblemDescriptor[] {descriptor};
    }
}
