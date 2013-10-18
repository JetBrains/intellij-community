package de.plushnikov.lombok;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.apache.log4j.Logger;

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

  private static final String LOMBOK_SRC_PATH = "./lombok-api/target/generated-sources/lombok";
  private static final String LOMBOKPG_SRC_PATH = "./lombok-api/target/generated-sources/lombok-pg";

  private static final Logger LOG = Logger.getLogger(LombokParsingTestCase.class);

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

  private PsiFile loadToPsiFile(String fileName) {
    VirtualFile virtualFile = myFixture.copyFileToProject(getBasePath() + "/" + fileName, fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    return myFixture.getFile();
  }

  public void doTest() throws IOException {
    doTest(getTestName(false).replace('$', '/') + ".java");
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

    compareClasses(intellijClasses, theirsClasses);
  }

  private void compareClasses(PsiClass[] intellijClasses, PsiClass[] theirsClasses) {
    assertEquals("Class counts are different", theirsClasses.length, intellijClasses.length);

    for (PsiClass theirsClass : theirsClasses) {
      boolean compared = false;
      for (PsiClass intellijClass : intellijClasses) {
        if (theirsClass.getName().equals(intellijClass.getName())) {
          compareTwoClasses(intellijClass, theirsClass);
          compared = true;
        }
      }
      assertTrue("Class names are not equal, class (" + theirsClass.getName() + ") not found", compared);
    }
  }

  private void compareTwoClasses(PsiClass intellijClass, PsiClass theirsClass) {
    LOG.info("Comparing classes IntelliJ " + intellijClass.getName() + " with " + theirsClass.getName());
    PsiModifierList intellijFieldModifierList = intellijClass.getModifierList();
    PsiModifierList theirsFieldModifierList = intellijClass.getModifierList();

    compareContainingClasses(intellijClass, theirsClass);
    compareModifiers(intellijFieldModifierList, theirsFieldModifierList);
    compareFields(intellijClass, theirsClass);
    compareMethods(intellijClass, theirsClass);
    compareConstructors(intellijClass, theirsClass);
    compareInnerClasses(intellijClass, theirsClass);

    LOG.info("Compared classes IntelliJ " + intellijClass.getName() + " with " + theirsClass.getName());
  }

  private void compareFields(PsiClass intellij, PsiClass theirs) {
    PsiField[] intellijFields = intellij.getFields();
    PsiField[] theirsFields = theirs.getFields();

    LOG.info("IntelliJ fields for class " + intellij.getName() + ": " + Arrays.toString(intellijFields));
    LOG.info("Theirs fields for class " + theirs.getName() + ": " + Arrays.toString(theirsFields));

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
      assertEquals(String.format("Types are not equal for element: %s", whereTarget.getName()), theirsCanonicalText, intellijCanonicalText);
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

    LOG.info("IntelliJ methods for class " + intellij.getName() + ": " + Arrays.toString(intellijMethods));
    LOG.info("Theirs methods for class " + theirs.getName() + ": " + Arrays.toString(theirsMethods));

    assertEquals("Method counts are different for Class: " + intellij.getName(), theirsMethods.length, intellijMethods.length);

    for (PsiMethod theirsMethod : theirsMethods) {
      boolean compared = false;
      final PsiModifierList theirsMethodModifierList = theirsMethod.getModifierList();
      for (PsiMethod intellijMethod : intellijMethods) {
        if (theirsMethod.getName().equals(intellijMethod.getName()) &&
            theirsMethod.getParameterList().getParametersCount() == intellijMethod.getParameterList().getParametersCount()) {
          PsiModifierList intellijMethodModifierList = intellijMethod.getModifierList();

          compareModifiers(intellijMethodModifierList, theirsMethodModifierList);
          compareType(intellijMethod.getReturnType(), theirsMethod.getReturnType(), theirsMethod);
          compareParams(intellijMethod.getParameterList(), theirsMethod.getParameterList());

          compared = true;
        }
      }
      assertTrue("Method names are not equal, Method: (" + theirsMethod.getName() + ") not found in class : " + intellij.getName(), compared);
    }
  }

  private void compareConstructors(PsiClass intellij, PsiClass theirs) {
    PsiMethod[] intellijConstructors = intellij.getConstructors();
    PsiMethod[] theirsConstructors = theirs.getConstructors();

    LOG.info("IntelliJ constructors for class " + intellij.getName() + ": " + Arrays.toString(intellijConstructors));
    LOG.info("Theirs constructors for class " + theirs.getName() + ": " + Arrays.toString(theirsConstructors));

    assertEquals("Constructor counts are different for Class: " + intellij.getName(), theirsConstructors.length, intellijConstructors.length);

    for (PsiMethod theirsConstructor : theirsConstructors) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = theirsConstructor.getModifierList();
      for (PsiMethod intellijConstructor : intellijConstructors) {
        if (theirsConstructor.getName().equals(intellijConstructor.getName()) &&
            theirsConstructor.getParameterList().getParametersCount() == intellijConstructor.getParameterList().getParametersCount()) {
          PsiModifierList intellijConstructorModifierList = intellijConstructor.getModifierList();

          compareModifiers(intellijConstructorModifierList, theirsFieldModifierList);
          compareType(intellijConstructor.getReturnType(), theirsConstructor.getReturnType(), theirsConstructor);
          compareParams(intellijConstructor.getParameterList(), theirsConstructor.getParameterList());

          compared = true;
        }
        assertTrue("Constructor names are not equal, Method: (" + theirsConstructor.getName() + ") not found in class : " + intellijConstructor.getName(), compared);
      }
    }
  }

  private void compareContainingClasses(PsiClass intellij, PsiClass theirs) {
    PsiClass intellijContainingClass = intellij.getContainingClass();
    PsiClass theirsContainingClass = theirs.getContainingClass();

    String intellijContainingClassName = intellijContainingClass == null ? null : intellijContainingClass.toString();
    String theirsContainingClassName = theirsContainingClass == null ? null : theirsContainingClass.toString();


    LOG.info("IntelliJ containing class for class " + intellij.getName() + ": " + intellijContainingClassName);
    LOG.info("Theirs containing class for class " + theirs.getName() + ": " + theirsContainingClassName);

    assertEquals("Containing classes different for class: " + intellij.getName(), intellijContainingClassName, theirsContainingClassName);
  }

  private void compareInnerClasses(PsiClass intellij, PsiClass theirs) {
    PsiClass[] intellijClasses = intellij.getInnerClasses();
    PsiClass[] theirsClasses = theirs.getInnerClasses();

    LOG.info("IntelliJ inner classes for class " + intellij.getName() + ": " + Arrays.toString(intellijClasses));
    LOG.info("Theirs inner classes for class " + theirs.getName() + ": " + Arrays.toString(theirsClasses));

    compareClasses(intellijClasses, theirsClasses);
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
