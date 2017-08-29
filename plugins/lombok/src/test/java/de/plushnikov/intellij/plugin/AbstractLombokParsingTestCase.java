package de.plushnikov.intellij.plugin;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.SortedList;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class AbstractLombokParsingTestCase extends AbstractLombokLightCodeInsightTestCase {

  private static final Logger LOG = Logger.getLogger(AbstractLombokParsingTestCase.class);

  protected boolean shouldCompareAnnotations() {
    return false;
  }

  protected boolean shouldCompareModifiers() {
    return true;
  }

  protected boolean shouldCompareCodeBlocks() {
    return true;
  }

  public void doTest() throws IOException {
    doTest(false);
  }

  public void doTest(final boolean lowercaseFirstLetter) throws IOException {
    doTest(getTestName(lowercaseFirstLetter));
  }

  public void doTest(String testName) throws IOException {
    compareFiles(loadBeforeLombokFile(testName), loadAfterDeLombokFile(testName));
  }

  protected PsiJavaFile loadBeforeLombokFile(String testName) {
    return getPsiJavaFile(testName, "before");
  }

  protected PsiJavaFile loadAfterDeLombokFile(String testName) {
    return getPsiJavaFile(testName, "after");
  }

  @NotNull
  private PsiJavaFile getPsiJavaFile(String testName, String type) {
    final String fileName = testName.replace('$', '/') + ".java";
    final String beforeFileName = type + "/" + fileName;
    final PsiFile psiFile = loadToPsiFile(beforeFileName);
    if (!(psiFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }
    return (PsiJavaFile) psiFile;
  }

  protected void compareFiles(PsiJavaFile beforeLombokFile, PsiJavaFile afterDelombokFile) {
    PsiClass[] beforeClasses = beforeLombokFile.getClasses();
    PsiClass[] afterClasses = afterDelombokFile.getClasses();

    compareClasses(beforeClasses, afterClasses);
  }

  private void compareClasses(PsiClass[] beforeClasses, PsiClass[] afterClasses) {
    String before = "Before classes: " + Arrays.toString(beforeClasses);
    String after = "After classes: " + Arrays.toString(afterClasses);

    assertEquals("Class counts are different " + before + " <> " + after, afterClasses.length, beforeClasses.length);

    for (PsiClass afterClass : afterClasses) {
      boolean compared = false;
      for (PsiClass beforeClass : beforeClasses) {
        if (Objects.equal(afterClass.getName(), beforeClass.getName())) {
          compareTwoClasses(beforeClass, afterClass);
          compared = true;
        }
      }
      assertTrue("Class names are not equal, class (" + afterClass.getName() + ") not found", compared);
    }
  }

  private void compareTwoClasses(PsiClass beforeClass, PsiClass afterClass) {
    LOG.info("Comparing classes " + beforeClass.getName() + " with " + afterClass.getName());
    PsiModifierList beforeFieldModifierList = beforeClass.getModifierList();
    PsiModifierList afterFieldModifierList = afterClass.getModifierList();

    compareContainingClasses(beforeClass, afterClass);
    compareModifiers(beforeFieldModifierList, afterFieldModifierList);
    compareFields(beforeClass, afterClass);
    compareMethods(beforeClass, afterClass);
    compareConstructors(beforeClass, afterClass);
    compareInnerClasses(beforeClass, afterClass);

    LOG.debug("Compared classes IntelliJ " + beforeClass.getName() + " with " + afterClass.getName());
  }

  private void compareFields(PsiClass beforeClass, PsiClass afterClass) {
    PsiField[] beforeClassFields = beforeClass.getFields();
    PsiField[] afterClassFields = afterClass.getFields();

    LOG.debug("IntelliJ fields for class " + beforeClass.getName() + ": " + Arrays.toString(beforeClassFields));
    LOG.debug("Theirs fields for class " + afterClass.getName() + ": " + Arrays.toString(afterClassFields));

    assertEquals("Field counts are different for Class " + beforeClass.getName(), afterClassFields.length, beforeClassFields.length);

    for (PsiField afterField : afterClassFields) {
      boolean compared = false;
      final PsiModifierList afterFieldModifierList = afterField.getModifierList();
      for (PsiField beforeField : beforeClassFields) {
        if (Objects.equal(afterField.getName(), beforeField.getName())) {
          final PsiModifierList beforeFieldModifierList = beforeField.getModifierList();

          compareModifiers(beforeFieldModifierList, afterFieldModifierList);
          compareType(beforeField.getType(), afterField.getType(), afterField);
          compareInitializers(beforeField.getInitializer(), afterField.getInitializer());
          compared = true;
        }
      }
      assertTrue("Fieldnames are not equal, Field (" + afterField.getName() + ") not found", compared);
    }
  }

  private void compareInitializers(PsiExpression beforeInitializer, PsiExpression afterInitializer) {
    String beforeInitializerText = null == beforeInitializer ? "" : beforeInitializer.getText();
    String afterInitializerText = null == afterInitializer ? "" : afterInitializer.getText();
    assertEquals("Initializers are not equals ", afterInitializerText, beforeInitializerText);
  }

  private void compareType(PsiType beforeType, PsiType afterType, PomNamedTarget whereTarget) {
    if (null != beforeType && null != afterType) {
      final String afterText = stripJavaLang(afterType.getCanonicalText());
      final String beforeText = stripJavaLang(beforeType.getCanonicalText());
      assertEquals(String.format("Types are not equal for element: %s", whereTarget.getName()), afterText, beforeText);
    }
  }

  private String stripJavaLang(String canonicalText) {
    final String prefix = "java.lang.";
    if (canonicalText.startsWith(prefix)) {
      canonicalText = canonicalText.substring(prefix.length());
    }
    return canonicalText;
  }

  private void compareModifiers(PsiModifierList beforeModifierList, PsiModifierList afterModifierList) {
    assertNotNull(beforeModifierList);
    assertNotNull(afterModifierList);

    if (shouldCompareModifiers()) {
      for (String modifier : PsiModifier.MODIFIERS) {
        boolean haveSameModifiers = afterModifierList.hasModifierProperty(modifier) == beforeModifierList.hasModifierProperty(modifier);
        final PsiMethod afterModifierListParent = PsiTreeUtil.getParentOfType(afterModifierList, PsiMethod.class);
        assertTrue(modifier + " Modifier is not equal for " + (null == afterModifierListParent ? "..." : afterModifierListParent.getText()), haveSameModifiers);
      }
    }

    if (shouldCompareAnnotations()) {
      Collection<String> beforeAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(beforeModifierList.getAnnotations()), new QualifiedNameFunction()));
      Collection<String> afterAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(afterModifierList.getAnnotations()), new QualifiedNameFunction()));

      Iterables.removeIf(beforeAnnotations, Predicates.containsPattern("lombok.*"));
      assertThat("Annotations are different", beforeAnnotations, equalTo(afterAnnotations));
    }
  }

  private void compareMethods(PsiClass beforeClass, PsiClass afterClass) {
    PsiMethod[] beforeMethods = beforeClass.getMethods();
    PsiMethod[] afterMethods = afterClass.getMethods();

    assertEquals("Methods are different for Class: " + beforeClass.getName(),
      Arrays.toString(toList(afterMethods)), Arrays.toString(toList(beforeMethods)));

    for (PsiMethod afterMethod : afterMethods) {

      final Collection<PsiMethod> matchedMethods = filterMethods(beforeMethods, afterMethod);
      if (matchedMethods.isEmpty()) {
        fail("Method names are not equal, Method: (" + afterMethod.getName() + ") not found in class : " + beforeClass.getName());
      }

      for (PsiMethod beforeMethod : matchedMethods) {
        compareMethod(beforeClass, afterClass, afterMethod, beforeMethod);
      }
    }
  }

  private void compareMethod(PsiClass beforeClass, PsiClass afterClass, PsiMethod afterMethod, PsiMethod beforeMethod) {
    final PsiModifierList afterModifierList = afterMethod.getModifierList();
    PsiModifierList beforeModifierList = beforeMethod.getModifierList();

    compareModifiers(beforeModifierList, afterModifierList);
    compareType(beforeMethod.getReturnType(), afterMethod.getReturnType(), afterMethod);
    compareParams(beforeMethod.getParameterList(), afterMethod.getParameterList());
    compareThrows(beforeMethod.getThrowsList(), afterMethod.getThrowsList(), afterMethod);

    if (shouldCompareCodeBlocks()) {
      final PsiCodeBlock beforeMethodBody = beforeMethod.getBody();
      final PsiCodeBlock afterMethodBody = afterMethod.getBody();
      if (null != beforeMethodBody && null != afterMethodBody) {

        boolean codeBlocksAreEqual = beforeMethodBody.textMatches(afterMethodBody);
        if (!codeBlocksAreEqual) {
          String text1 = beforeMethodBody.getText().replaceAll("java\\.lang\\.", "").replaceAll("\\s+", "");
          String text2 = afterMethodBody.getText().replaceAll("java\\.lang\\.", "").replaceAll("\\s+", "");
          assertEquals("Methods not equal, Method: (" + afterMethod.getName() + ") Class:" + afterClass.getName(), text2, text1);
        }
      } else {
        if (null != afterMethodBody) {
          fail("MethodCodeBlocks is null: Method: (" + beforeMethod.getName() + ") Class:" + beforeClass.getName());
        }
      }
    }
  }

  private Collection<PsiMethod> filterMethods(PsiMethod[] beforeMethods, PsiMethod compareMethod) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    for (PsiMethod psiMethod : beforeMethods) {
      final PsiParameterList compareMethodParameterList = compareMethod.getParameterList();
      final PsiParameterList psiMethodParameterList = psiMethod.getParameterList();
      if (compareMethod.getName().equals(psiMethod.getName()) &&
        compareMethodParameterList.getParametersCount() == psiMethodParameterList.getParametersCount()) {
        final Collection<String> typesOfCompareMethod = mapToTypeString(compareMethodParameterList);
        final Collection<String> typesOfPsiMethod = mapToTypeString(psiMethodParameterList);
        if (typesOfCompareMethod.equals(typesOfPsiMethod)) {
          result.add(psiMethod);
        }
      }
    }
    return result;
  }

  @NotNull
  private Collection<String> mapToTypeString(PsiParameterList compareMethodParameterList) {
    Collection<String> result = new ArrayList<String>();
    final PsiParameter[] compareMethodParameterListParameters = compareMethodParameterList.getParameters();
    for (PsiParameter compareMethodParameterListParameter : compareMethodParameterListParameters) {
      result.add(stripJavaLang(compareMethodParameterListParameter.getType().getCanonicalText()));
    }
    return result;
  }

  private String[] toList(PsiMethod[] beforeMethods) {
    SortedList<String> result = new SortedList<String>(String.CASE_INSENSITIVE_ORDER);
    for (PsiMethod method : beforeMethods) {
      result.add(method.getName());
    }
    return result.toArray(new String[result.size()]);
  }

  private void compareThrows(PsiReferenceList beforeThrows, PsiReferenceList afterThrows, PsiMethod psiMethod) {
    PsiClassType[] beforeTypes = beforeThrows.getReferencedTypes();
    PsiClassType[] afterTypes = afterThrows.getReferencedTypes();

    assertEquals("Throws counts are different for Method :" + psiMethod.getName(), beforeTypes.length, afterTypes.length);
    for (PsiClassType beforeType : beforeTypes) {
      boolean found = false;
      for (PsiClassType afterType : afterTypes) {
        if (beforeType.equals(afterType)) {
          found = true;
          break;
        }
      }
      assertTrue("Expected throw: " + beforeType.getClassName() + " not found on " + psiMethod.getName(), found);
    }
  }

  private void compareConstructors(PsiClass beforeClass, PsiClass afterClass) {
    PsiMethod[] beforeConstructors = beforeClass.getConstructors();
    PsiMethod[] afterConstructors = afterClass.getConstructors();

    LOG.debug("IntelliJ constructors for class " + beforeClass.getName() + ": " + Arrays.toString(beforeConstructors));
    LOG.debug("Theirs constructors for class " + afterClass.getName() + ": " + Arrays.toString(afterConstructors));

    assertEquals("Constructor counts are different for Class: " + beforeClass.getName(), afterConstructors.length, beforeConstructors.length);

    for (PsiMethod afterConstructor : afterConstructors) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = afterConstructor.getModifierList();
      for (PsiMethod beforeConstructor : beforeConstructors) {
        if (afterConstructor.getName().equals(beforeConstructor.getName()) &&
          afterConstructor.getParameterList().getParametersCount() == beforeConstructor.getParameterList().getParametersCount()) {
          PsiModifierList intellijConstructorModifierList = beforeConstructor.getModifierList();

          compareModifiers(intellijConstructorModifierList, theirsFieldModifierList);
          compareType(beforeConstructor.getReturnType(), afterConstructor.getReturnType(), afterConstructor);
          compareParams(beforeConstructor.getParameterList(), afterConstructor.getParameterList());

          compared = true;
          break;
        }

      }
      assertTrue("Constructor names are not equal, Method: (" + afterConstructor.getName() + ") not found in class : " + beforeClass.getName(), compared);
    }
  }

  private void compareContainingClasses(PsiClass intellij, PsiClass theirs) {
    PsiClass intellijContainingClass = intellij.getContainingClass();
    PsiClass theirsContainingClass = theirs.getContainingClass();

    String intellijContainingClassName = intellijContainingClass == null ? null : intellijContainingClass.toString();
    String theirsContainingClassName = theirsContainingClass == null ? null : theirsContainingClass.toString();

    LOG.debug("IntelliJ containing class for class " + intellij.getName() + ": " + intellijContainingClassName);
    LOG.debug("Theirs containing class for class " + theirs.getName() + ": " + theirsContainingClassName);

    assertEquals("Containing classes different for class: " + intellij.getName(), intellijContainingClassName, theirsContainingClassName);
  }

  private void compareInnerClasses(PsiClass intellij, PsiClass theirs) {
    PsiClass[] intellijClasses = intellij.getInnerClasses();
    PsiClass[] theirsClasses = theirs.getInnerClasses();

    LOG.debug("IntelliJ inner classes for class " + intellij.getName() + ": " + Arrays.toString(intellijClasses));
    LOG.debug("Theirs inner classes for class " + theirs.getName() + ": " + Arrays.toString(theirsClasses));

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
