package com.intellij.refactoring.psi;

import com.intellij.psi.PsiNameHelper;

import java.util.StringTokenizer;

public class PackageNameUtil {
    private PackageNameUtil() {
    }

    public static boolean containsNonIdentifier(PsiNameHelper nameHelper, String packageName) {

        final StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
        while(tokenizer.hasMoreTokens())
        {
            final String component = tokenizer.nextToken();
            if (!nameHelper.isIdentifier(component)) {
                return true;
            }
        }
        return false;
    }
}
