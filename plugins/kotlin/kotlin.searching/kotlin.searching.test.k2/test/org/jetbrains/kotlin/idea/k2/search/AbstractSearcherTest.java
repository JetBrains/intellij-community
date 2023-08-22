// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.search;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.Query;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils;
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase;
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor;
import org.jetbrains.kotlin.idea.test.TestUtilsKt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractSearcherTest extends KotlinLightCodeInsightFixtureTestCase {
    @Override
    public void setUp() {
        super.setUp();
        TestUtilsKt.invalidateLibraryCache(getProject());
    }

    protected PsiClass getPsiClass(String className) {
        PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, getGlobalScope());
        assertNotNull("Couldn't find a psiClass: " + className, psiClass);
        return psiClass;
    }

    private GlobalSearchScope getGlobalScope() {
        return GlobalSearchScope.allScope(getProject());
    }

    protected GlobalSearchScope getProjectScope() {
        return GlobalSearchScope.projectScope(getProject());
    }

    protected static void checkResult(@NotNull File file, Query<?> actual) throws IOException {
        String text = FileUtil.loadFile(file, true);

        List<String> classFqnFilters = InTextDirectivesUtils.findListWithPrefixes(text, "// IGNORE_CLASSES: ");

        List<String> actualModified = new ArrayList<>();
        for (Object member : actual) {
            if (member instanceof PsiClass) {
                String qualifiedName = ((PsiClass) member).getQualifiedName();
                if (qualifiedName == null) {
                    continue;
                }

                boolean filterOut = CollectionsKt.any(classFqnFilters, qualifiedName::startsWith);

                if (filterOut) {
                    continue;
                }
            }

            actualModified.add(stringRepresentation(member));
        }
        Collections.sort(actualModified);

        List<String> expected = InTextDirectivesUtils.findListWithPrefixes(text, "// SEARCH: ");
        Collections.sort(expected);

        assertOrderedEquals(actualModified, expected);
    }

    protected void checkClassWithDirectives(@NotNull String unused) throws IOException {
        File testDataFile = dataFile();
        myFixture.configureByFile(fileName());
        List<String> directives = InTextDirectivesUtils.findListWithPrefixes(FileUtil.loadFile(testDataFile, true), "// CLASS: ");
        assertFalse("Specify CLASS directive in test file", directives.isEmpty());
        String superClassName = directives.get(0);
        PsiClass psiClass = getPsiClass(superClassName);
        checkResult(testDataFile, ClassInheritorsSearch.search(psiClass, getProjectScope(), false));
    }

    private static String stringRepresentation(Object member) {
        if (member instanceof PsiClass) {
            return "class:" + ((PsiClass) member).getName();
        }
        if (member instanceof PsiMethod) {
            return "method:" + ((PsiMethod) member).getName();
        }
        if (member instanceof PsiField) {
            return "field:" + ((PsiField) member).getName();
        }
        if (member instanceof PsiParameter) {
            return "param:" + ((PsiParameter) member).getName();
        }
        throw new IllegalStateException("Do not know how to render member of type: " + member.getClass().getName());
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return KotlinLightProjectDescriptor.INSTANCE;
    }
}
