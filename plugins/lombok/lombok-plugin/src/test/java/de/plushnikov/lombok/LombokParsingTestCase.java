package de.plushnikov.lombok;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addLombokClassesToFixture();

    //myFixture.copyDirectoryToProject("", "")
  }

  private void addLombokClassesToFixture() {
    try {
      //added java.lang.Object to 'classpath'
      myFixture.addClass("package java.lang; public class Object {}");

      // added some classes used by tests to 'classpath'
      myFixture.addClass("package java.util; public class Timer {}");

      // added lombok classes used by tests to 'classpath'
      myFixture.addClass(PACKAGE_LOMBOK +
          "public enum AccessLevel {\n" +
          "  PUBLIC, MODULE, PROTECTED, PACKAGE, PRIVATE, NONE;\n" +
          "}");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface AllArgsConstructor {\n" +
          "  String staticName() default \"\";\n" +
          "  AccessLevel access() default AccessLevel.PUBLIC;\n" +
          "  @Deprecated boolean suppressConstructorProperties() default false;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface NoArgsConstructor {  \n" +
          "  String staticName() default \"\";\n" +
          "  AccessLevel access() default AccessLevel.PUBLIC;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface RequiredArgsConstructor {\n" +
          "  String staticName() default \"\";\n" +
          "  AccessLevel access() default AccessLevel.PUBLIC;\n" +
          "  @Deprecated boolean suppressConstructorProperties() default false;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface Cleanup {\n" +
          "  String value() default \"close\";\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface Data {  \n" +
          "  String staticConstructor() default \"\";\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface EqualsAndHashCode {\n" +
          "  String[] exclude() default {};\n" +
          "  String[] of() default {};\n" +
          "  boolean callSuper() default false;\n" +
          "  boolean doNotUseGetters() default false;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface Getter {\n" +
          "  AccessLevel value() default AccessLevel.PUBLIC;\n" +
          "  boolean lazy() default false;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface Setter {\n" +
          "  AccessLevel value() default AccessLevel.PUBLIC;\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface Synchronized {  \n" +
          "  String value() default \"\";\n" +
          "}\n");
      myFixture.addClass(PACKAGE_LOMBOK + ANNOTATION_TYPE +
          "public @interface ToString {  \n" +
          "  boolean includeFieldNames() default true;\n" +
          "  String[] exclude() default {};\n" +
          "  String[] of() default {};\n" +
          "  boolean callSuper() default false;\n" +
          "  boolean doNotUseGetters() default false;\n" +
          "}");

      myFixture.addClass("package lombok.extern.apachecommons;\n" + ANNOTATION_TYPE +
          "public @interface CommonsLog {\n" +
          "}");
      myFixture.addClass("package lombok.extern.java;\n" + ANNOTATION_TYPE +
          "public @interface Log {\n" +
          "}");
      myFixture.addClass("package lombok.extern.log4j;\n" + ANNOTATION_TYPE +
          "public @interface Log4j {\n" +
          "}");
      myFixture.addClass("package lombok.extern.log4j;\n" + ANNOTATION_TYPE +
          "public @interface Log4j2 {\n" +
          "}");
      myFixture.addClass("package lombok.extern.slf4j;\n" + ANNOTATION_TYPE +
          "public @interface Slf4j {\n" +
          "}");
      myFixture.addClass("package lombok.extern.slf4j;\n" + ANNOTATION_TYPE +
          "public @interface XSlf4j {\n" +
          "}");
    } catch (Exception ex) {
      System.err.println("Error occured ");
      ex.printStackTrace(System.err);
    }
  }

  public void doTest() throws IOException {
    doTest(getTestName(true).replace('$', '/') + ".java");
  }

  protected void doTest(String fileName) throws IOException {
    final PsiFile psiLombokFile = createPseudoPhysicalFile(getProject(), fileName, loadLombokFile(fileName));
    final PsiFile psiDelombokFile = createPseudoPhysicalFile(getProject(), fileName, loadDeLombokFile(fileName));

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

    PsiAnnotation[] intellijAnnotations = intellij.getAnnotations();
    PsiAnnotation[] theirsAnnotations = theirs.getAnnotations();

    //assertEquals("Annotationcounts are different ", theirsAnnotations.length, intellijAnnotations.length);
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

  @Override
  protected String getTestDataPath() {
    return "./lombok-plugin/src/test/data";
  }

  private String loadFileContent(String subDir, String fileName) throws IOException {
    final File fromFile = new File(getTestDataPath(), subDir);
    String text = FileUtil.loadFile(new File(fromFile, fileName), CharsetToolkit.UTF8).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }

}
