package de.plushnikov.lombok;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class LombokParsingTestCase extends LightCodeInsightFixtureTestCase {

  private static final Set<String> modifiers = new HashSet<String>(Arrays.asList(
      PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC,
      PsiModifier.ABSTRACT, PsiModifier.SYNCHRONIZED, PsiModifier.TRANSIENT, PsiModifier.VOLATILE, PsiModifier.NATIVE));

  public static final String PACKAGE_LOMBOK = "package lombok;\n";
  public static final String ANNOTATION_TYPE = "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)\n" +
      "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)\n";

  private static final String LOMBOK_SRC_PATH = "./lombok-api/target/generated-sources";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addLombokClassesToFixture();
  }

  private void addLombokClassesToFixture() {
    //added java.lang.Object to 'classpath'
    myFixture.addClass("package java.lang; public class Object {}");

    // added some classes used by tests to 'classpath'
    myFixture.addClass("package java.util; public class Timer {}");

    List<File> filesByMask = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), new File(LOMBOK_SRC_PATH));
    for (File javaFile : filesByMask) {
      myFixture.configureByFile(javaFile.getPath().replace("\\", "/"));
    }
  }

  public void doTest() throws IOException {
    doTest(getTestName(true).replace('$', '/') + ".java");
  }

  protected void doTest(String fileName) throws IOException {
//    final PsiFile psiDelombokFile = myFixture.configureByText(StdFileTypes.JAVA, loadDeLombokFile(fileName));//createPseudoPhysicalFile(getProject(), fileName, loadDeLombokFile(fileName));
//    final PsiFile psiLombokFile = myFixture.configureByText(StdFileTypes.JAVA, loadLombokFile(fileName));//createPseudoPhysicalFile(getProject(), fileName, loadLombokFile(fileName));
    final PsiFile psiDelombokFile = createPseudoPhysicalFile(getProject(), fileName, loadDeLombokFile(fileName));
    final PsiFile psiLombokFile = createPseudoPhysicalFile(getProject(), fileName, loadLombokFile(fileName));

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

    assertEquals("Methodscounts are different for Class " + intellij.getName(), theirsMethods.length, intellijMethods.length);

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
      assertTrue("Methodnames are not equal, Method (" + theirsMethod.getName() + ") not found : " + intellij.getName(), compared);
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

  protected PsiFile createPseudoPhysicalFile(final Project project, final String fileName, final String text) throws IncorrectOperationException {
    return PsiFileFactory.getInstance(project).createFileFromText(
        fileName,
        FileTypeManager.getInstance().getFileTypeByFileName(fileName),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  protected String loadLombokFile(String fileName) throws IOException {
    return loadFileContent("/before/", fileName);
  }

  protected String loadDeLombokFile(String fileName) throws IOException {
    return loadFileContent("/after/", fileName);
  }

  protected String getLombokTestDataDirectory() {
    return "./lombok-plugin/src/test/data";
  }

  @Override
  protected String getTestDataPath() {
    return "";
  }

  private String loadFileContent(String subDir, String fileName) throws IOException {
    final File fromFile = new File(getLombokTestDataDirectory(), subDir);
    String text = FileUtil.loadFile(new File(fromFile, fileName), CharsetToolkit.UTF8).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

  private static class QualifiedNameFunction implements Function<PsiAnnotation, String> {
    @Override
    public String apply(PsiAnnotation psiAnnotation) {
      return psiAnnotation.getQualifiedName();
    }
  }
}
