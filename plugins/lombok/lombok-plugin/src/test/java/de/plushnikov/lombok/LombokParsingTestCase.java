package de.plushnikov.lombok;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class LombokParsingTestCase extends LombokLightCodeInsightTestCase {

  private static final Logger LOG = Logger.getLogger(LombokParsingTestCase.class);
  private static final Collection<String> MODIFIERS_TO_COMPARE = Collections2.filter(Arrays.asList(PsiModifier.MODIFIERS), Predicates.not(Predicates.equalTo(PsiModifier.DEFAULT)));

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
    doTest(getTestName(false).replace('$', '/') + ".java");
  }

  protected void doTest(String fileName) throws IOException {
    final PsiFile psiDelombokFile = loadToPsiFile("after/" + fileName);
    final PsiFile psiLombokFile = loadToPsiFile("before/" + fileName);

    if (!(psiLombokFile instanceof PsiJavaFile) || !(psiDelombokFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }

    final PsiJavaFile beforeFile = (PsiJavaFile) psiLombokFile;
    final PsiJavaFile afterFile = (PsiJavaFile) psiDelombokFile;

    PsiClass[] beforeClasses = beforeFile.getClasses();
    PsiClass[] afterClasses = afterFile.getClasses();

    compareClasses(beforeClasses, afterClasses);
  }

  private void compareClasses(PsiClass[] beforeClasses, PsiClass[] afterClasses) {
    assertEquals("Class counts are different", afterClasses.length, beforeClasses.length);

    for (PsiClass afterClass : afterClasses) {
      boolean compared = false;
      for (PsiClass beforeClass : beforeClasses) {
        if (afterClass.getName().equals(beforeClass.getName())) {
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
      for (PsiField beeforeField : beforeClassFields) {
        if (afterField.getName().equals(beeforeField.getName())) {
          final PsiModifierList beforeFieldModifierList = beeforeField.getModifierList();

          compareModifiers(beforeFieldModifierList, afterFieldModifierList);
          compareType(beeforeField.getType(), afterField.getType(), afterField);
          compared = true;
        }
      }
      assertTrue("Fieldnames are not equal, Field (" + afterField.getName() + ") not found", compared);
    }
  }

  private void compareType(PsiType beforeType, PsiType afterType, PomNamedTarget whereTarget) {
    if (null != beforeType && null != afterType) {
      final String theirsCanonicalText = stripJavaLang(afterType.getCanonicalText());
      final String intellijCanonicalText = stripJavaLang(beforeType.getCanonicalText());
      assertEquals(String.format("Types are not equal for element: %s", whereTarget.getName()), theirsCanonicalText, intellijCanonicalText);
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
      for (String modifier : MODIFIERS_TO_COMPARE) {
        assertEquals(modifier + " Modifier is not equal; ", afterModifierList.hasModifierProperty(modifier), beforeModifierList.hasModifierProperty(modifier));
      }
    }
    if (shouldCompareAnnotations()) {
      Collection<String> beforeAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(beforeModifierList.getAnnotations()), new QualifiedNameFunction()));
      Collection<String> afterAnnotations = Lists.newArrayList(Collections2.transform(Arrays.asList(afterModifierList.getAnnotations()), new QualifiedNameFunction()));

      Iterables.removeIf(beforeAnnotations, Predicates.containsPattern("lombok.*"));
      assertEquals("Annotationcounts are different ", afterAnnotations.size(), beforeAnnotations.size());
    }
  }


  private void compareMethods(PsiClass beforeClass, PsiClass afterClass) {
    PsiMethod[] beforeMethods = beforeClass.getMethods();
    PsiMethod[] afterMethods = afterClass.getMethods();

    LOG.info("Before methods for class " + beforeClass.getName() + ": " + Arrays.toString(beforeMethods));
    LOG.info("After methods for class " + afterClass.getName() + ": " + Arrays.toString(afterMethods));

    assertEquals("Method counts are different for Class: " + beforeClass.getName(), afterMethods.length, beforeMethods.length);

    for (PsiMethod afterMethod : afterMethods) {
      boolean compared = false;
      final PsiModifierList afterModifierList = afterMethod.getModifierList();
      for (PsiMethod beforeMethod : beforeMethods) {
        if (afterMethod.getName().equals(beforeMethod.getName()) &&
            afterMethod.getParameterList().getParametersCount() == beforeMethod.getParameterList().getParametersCount()) {
          PsiModifierList beforeModifierList = beforeMethod.getModifierList();

          compareModifiers(beforeModifierList, afterModifierList);
          compareType(beforeMethod.getReturnType(), afterMethod.getReturnType(), afterMethod);
          compareParams(beforeMethod.getParameterList(), afterMethod.getParameterList());

          if (shouldCompareCodeBlocks()) {
            final PsiCodeBlock beforeMethodBody = beforeMethod.getBody();
            final PsiCodeBlock afterMethodBody = afterMethod.getBody();
            if (null != beforeMethodBody && null != afterMethodBody) {

              boolean codeBlocksAreEqual = beforeMethodBody.textMatches(afterMethodBody);
              if (!codeBlocksAreEqual) {
                String text1 = beforeMethodBody.getText().replaceAll("\\s+", "");
                String text2 = afterMethodBody.getText().replaceAll("\\s+", "");
                assertEquals("Methods not equal, Method: (" + afterMethod.getName() + ") Class:" + afterClass.getName(), text2, text1);
              }
            } else {
              if (null != afterMethodBody) {
                fail("MethodCodeBlocks is null: Method: (" + beforeMethod.getName() + ") Class:" + beforeClass.getName());
              }
            }
          }

          compared = true;
        }
      }
      assertTrue("Method names are not equal, Method: (" + afterMethod.getName() + ") not found in class : " + beforeClass.getName(), compared);
    }
  }

  private void compareConstructors(PsiClass intellij, PsiClass theirs) {
    PsiMethod[] intellijConstructors = intellij.getConstructors();
    PsiMethod[] theirsConstructors = theirs.getConstructors();

    LOG.debug("IntelliJ constructors for class " + intellij.getName() + ": " + Arrays.toString(intellijConstructors));
    LOG.debug("Theirs constructors for class " + theirs.getName() + ": " + Arrays.toString(theirsConstructors));

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
          break;
        }

      }
      assertTrue("Constructor names are not equal, Method: (" + theirsConstructor.getName() + ") not found in class : " + intellij.getName(), compared);
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
