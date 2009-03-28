package com.intellij.testFramework.fixtures.impl;

import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public class JavaCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements JavaCodeInsightTestFixture {
  public JavaCodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture) {
    super(projectFixture);
  }

  public JavaPsiFacade getJavaFacade() {
    assertInitialized();
    return JavaPsiFacade.getInstance(getProject());
  }

  public PsiClass addClass(@NotNull @NonNls final String classText) throws IOException {
    assertInitialized();
    final PsiClass psiClass = addClass(getTempDirPath(), classText);
    myAddedClasses.add(psiClass.getContainingFile().getVirtualFile());
    return psiClass;
  }

  private PsiClass addClass(@NonNls String rootPath, @NotNull @NonNls final String classText) throws IOException {
    final PsiClass aClass = ((PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", classText)).getClasses()[0];
    final String qName = aClass.getQualifiedName();
    assert qName != null;

    final PsiFile psiFile = ((HeavyIdeaTestFixtureImpl) myProjectFixture).addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText);

    return ((PsiJavaFile)psiFile).getClasses()[0];
  }
}

