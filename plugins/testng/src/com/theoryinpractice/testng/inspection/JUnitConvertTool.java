package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiClass;
import com.theoryinpractice.testng.util.Intentions;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class JUnitConvertTool extends LocalInspectionTool
{
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "TestNG";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Convert JUnit Tests to TestNG";
    }

    @NotNull
    @Override
    public String getShortName() {
        return "JUnitTestNG";
    }

    public boolean isEnabledByDefault() {
        return true;
    }
    
    @Override
    @Nullable
    public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
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
