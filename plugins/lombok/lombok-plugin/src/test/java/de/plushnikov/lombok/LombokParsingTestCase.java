package de.plushnikov.lombok;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class LombokParsingTestCase extends LightCodeInsightFixtureTestCase {

  private static final Set<String> modifiers = new HashSet<String>(Arrays.asList(
      PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC,
      PsiModifier.ABSTRACT, PsiModifier.SYNCHRONIZED, PsiModifier.TRANSIENT, PsiModifier.VOLATILE, PsiModifier.NATIVE));

  private static final String LOMBOK_SRC_PATH = "./lombok-api/target/generated-sources/lombok";
  private static final String LOMBOKPG_SRC_PATH = "./lombok-api/target/generated-sources/lombok-pg";

  @Override
  protected String getTestDataPath() {
    return ".";
  }

  @Override
  protected String getBasePath() {
    return "lombok-plugin/src/test/data";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addLombokClassesToFixture();
  }

  private void addLombokClassesToFixture() {
    loadFilesFrom(LOMBOK_SRC_PATH);
    loadFilesFrom(LOMBOKPG_SRC_PATH);
  }

  private void loadFilesFrom(final String srcPath) {
    List<File> filesByMask = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), new File(srcPath));
    for (File javaFile : filesByMask) {
      String filePath = javaFile.getPath().replace("\\", "/");
      myFixture.copyFileToProject(filePath, filePath.substring(srcPath.length() + 1));
    }
  }

  public void doTest() throws IOException {
    doTest(getTestName(true).replace('$', '/') + ".java");
  }

  protected void doTest(String fileName) throws IOException {
    final PsiFile psiDelombokFile = loadToPsiFile("after/" + fileName);
    final PsiFile psiLombokFile = loadToPsiFile("before/" + fileName);

    if (!(psiLombokFile instanceof PsiJavaFile) || !(psiDelombokFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }

    final PsiJavaFile intellij = (PsiJavaFile) psiLombokFile;
    final PsiJavaFile theirs = (PsiJavaFile) psiDelombokFile;

    PsiClass[] intellijClasses = intellij.getClasses();
    PsiClass[] theirsClasses = theirs.getClasses();
    assertEquals("Class counts are different", theirsClasses.length, intellijClasses.length);

    for (PsiClass theirsClass : theirsClasses) {
      boolean compared = false;
      for (PsiClass intellijClass : intellijClasses) {
        if (theirsClass.getName().equals(intellijClass.getName())) {
          compareFields(intellijClass, theirsClass);
          compareMethods(intellijClass, theirsClass);
          compared = true;
        }
      }
      assertTrue("Classnames are not equal, class (" + theirsClass.getName() + ") not found", compared);
    }
  }

  private PsiFile loadToPsiFile(String fileName) {
    VirtualFile virtualFile = myFixture.copyFileToProject(getBasePath() + "/" + fileName, fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return myFixture.getFile();
  }

  private void compareFields(PsiClass intellij, PsiClass theirs) {
    PsiField[] intellijFields = intellij.getFields();
    PsiField[] theirsFields = theirs.getFields();

    assertEquals("Field counts are different for Class " + intellij.getName(), theirsFields.length, intellijFields.length);

    for (PsiField theirsField : theirsFields) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = theirsField.getModifierList();
      for (PsiField intellijField : intellijFields) {
        if (theirsField.getName().equals(intellijField.getName())) {
          final PsiModifierList intellijFieldModifierList = intellijField.getModifierList();

          compareModifiers(intellijFieldModifierList, theirsFieldModifierList);
          compareType(intellijField.getType(), theirsField.getType(), theirsField);
          compared = true;
        }
      }
      assertTrue("Fieldnames are not equal, Field (" + theirsField.getName() + ") not found", compared);
    }
  }

  private void compareType(PsiType intellij, PsiType theirs, PomNamedTarget whereTarget) {
    if (null != intellij && null != theirs) {
      final String theirsCanonicalText = stripJavaLang(theirs.getCanonicalText());
      final String intellijCanonicalText = stripJavaLang(intellij.getCanonicalText());
      assertEquals("Types are not equal for: " + whereTarget.getName(), theirsCanonicalText, intellijCanonicalText);
    }
  }

  private String stripJavaLang(String theirsCanonicalText) {
    final String prefix = "java.lang.";
    if (theirsCanonicalText.startsWith(prefix)) {
      theirsCanonicalText = theirsCanonicalText.substring(prefix.length());
    }
    return theirsCanonicalText;
  }

  private void compareModifiers(PsiModifierList intellij, PsiModifierList theirs) {
    assertNotNull(intellij);
    assertNotNull(theirs);

    for (String modifier : modifiers) {
      assertEquals(modifier + " Modifier is not equal; ", theirs.hasModifierProperty(modifier), intellij.hasModifierProperty(modifier));
    }

    Collection<String> intellijAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(intellij.getAnnotations()), new QualifiedNameFunction()));
    Collection<String> theirsAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(theirs.getAnnotations()), new QualifiedNameFunction()));

    Iterables.removeIf(intellijAnnotations, Predicates.containsPattern("lombok.*"));
    //TODO assertEquals("Annotationcounts are different ", theirsAnnotations.size(), intellijAnnotations.size());
  }

  private void compareMethods(PsiClass intellij, PsiClass theirs) {
    PsiMethod[] intellijMethods = intellij.getMethods();
    PsiMethod[] theirsMethods = theirs.getMethods();

    assertEquals("Methodscounts are different for Class: " + intellij.getName(), theirsMethods.length, intellijMethods.length);

    for (PsiMethod theirsMethod : theirsMethods) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = theirsMethod.getModifierList();
      for (PsiMethod intellijMethod : intellijMethods) {
        if (theirsMethod.getName().equals(intellijMethod.getName()) &&
            theirsMethod.getParameterList().getParametersCount() == intellijMethod.getParameterList().getParametersCount()) {
          PsiModifierList intellijFieldModifierList = intellijMethod.getModifierList();

          compareModifiers(intellijFieldModifierList, theirsFieldModifierList);
          compareType(intellijMethod.getReturnType(), theirsMethod.getReturnType(), theirsMethod);
          compareParams(intellijMethod.getParameterList(), theirsMethod.getParameterList());

          compared = true;
        }
      }
      assertTrue("Methodnames are not equal, Method: (" + theirsMethod.getName() + ") not found in class : " + intellij.getName(), compared);
    }
  }

  private void compareParams(PsiParameterList intellij, PsiParameterList theirs) {
    assertEquals(theirs.getParametersCount(), intellij.getParametersCount());

    PsiParameter[] intellijParameters = intellij.getParameters();
    PsiParameter[] theirsParameters = theirs.getParameters();
    for (int i = 0; i < intellijParameters.length; i++) {
      PsiParameter intellijParameter = intellijParameters[i];
      PsiParameter theirsParameter = theirsParameters[i];

      compareType(intellijParameter.getType(), theirsParameter.getType(), theirsParameter);
    }
  }

  private static class QualifiedNameFunction implements Function<PsiAnnotation, String> {
    @Override
    public String apply(PsiAnnotation psiAnnotation) {
      return psiAnnotation.getQualifiedName();
    }
  }
}
