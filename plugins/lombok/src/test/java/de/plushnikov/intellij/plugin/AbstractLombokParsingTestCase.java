package de.plushnikov.intellij.plugin;

import com.google.common.base.Objects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class AbstractLombokParsingTestCase extends AbstractLombokLightCodeInsightTestCase {

  private static final Logger LOG = Logger.getInstance(AbstractLombokParsingTestCase.class);

  protected boolean shouldCompareAnnotations() {
    return !".*".equals(annotationToComparePattern());
  }

  protected String annotationToComparePattern() {
    return ".*";
  }

  protected Collection<String> annotationsToIgnoreList() {
    return Set.of("java.lang.SuppressWarnings", "java.lang.Override","com.fasterxml.jackson.databind.annotation.JsonDeserialize");
  }

  protected boolean shouldCompareCodeBlocks() {
    return true;
  }

  public void doTest() {
    doTest(false);
  }

  public void doTest(final boolean lowercaseFirstLetter) {
    doTest(getTestName(lowercaseFirstLetter));
  }

  public void doTest(String testName) {
    compareFiles(loadBeforeLombokFile(testName), loadAfterDeLombokFile(testName));
  }

  private PsiJavaFile loadBeforeLombokFile(String testName) {
    return getPsiJavaFile(testName, "before");
  }

  private PsiJavaFile loadAfterDeLombokFile(String testName) {
    return getPsiJavaFile(testName, "after");
  }

  @NotNull
  private PsiJavaFile getPsiJavaFile(String testName, String type) {
    final String fileName = testName.replace('$', '/') + ".java";
    final String filePath = type + "/" + fileName;
    final PsiFile psiFile = loadToPsiFile(filePath);
    if (!(psiFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }
    return (PsiJavaFile)psiFile;
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

    assertEquals("Field are different for Class: " + beforeClass.getName(),
                 Arrays.toString(toList(afterClassFields)), Arrays.toString(toList(beforeClassFields)));

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

  private static void compareInitializers(PsiExpression beforeInitializer, PsiExpression afterInitializer) {
    String beforeInitializerText = null == beforeInitializer ? "" : beforeInitializer.getText();
    String afterInitializerText = null == afterInitializer ? "" : afterInitializer.getText();
    assertEquals("Initializers are not equals ", afterInitializerText, beforeInitializerText);
  }

  private static void compareType(PsiType beforeType, PsiType afterType, PomNamedTarget whereTarget) {
    if (null != beforeType && null != afterType) {
      final String afterText = stripJavaLang(afterType.getCanonicalText());
      final String beforeText = stripJavaLang(beforeType.getCanonicalText());
      assertEquals(String.format("Types are not equal for element: %s", whereTarget.getName()), afterText, beforeText);
    }
  }

  private static String stripJavaLang(String canonicalText) {
    return StringUtil.trimStart(canonicalText, "java.lang.");
  }

  private void compareModifiers(PsiModifierList beforeModifierList, PsiModifierList afterModifierList) {
    assertNotNull(beforeModifierList);
    assertNotNull(afterModifierList);

    for (String modifier : PsiModifier.MODIFIERS) {
      boolean haveSameModifiers = afterModifierList.hasModifierProperty(modifier) == beforeModifierList.hasModifierProperty(modifier);
      if (!haveSameModifiers) {
        final PsiMethod afterModifierListParentMethod = PsiTreeUtil.getContextOfType(afterModifierList, PsiMethod.class);
        final PsiMethod afterModifierListParentField = PsiTreeUtil.getContextOfType(afterModifierList, PsiMethod.class);
        final PsiClass afterModifierListParentClass = PsiTreeUtil.getContextOfType(afterModifierList, PsiClass.class);
        final String target;
        if (afterModifierListParentMethod != null) {
          target = afterModifierListParentMethod.getText();
        }
        else if (afterModifierListParentField != null) {
          target = afterModifierListParentField.getName();
        }
        else {
          target = afterModifierListParentClass.getName();
        }
        fail(modifier + " Modifier is not equal for " + target);
      }
    }

    compareAnnotations(beforeModifierList, afterModifierList);
  }

  private void compareAnnotations(PsiModifierList beforeModifierList, PsiModifierList afterModifierList) {
    if (shouldCompareAnnotations()) {
      Collection<String> beforeAnnotations = Arrays.stream(beforeModifierList.getAnnotations())
        .map(PsiAnnotation::getQualifiedName)
        .filter(Pattern.compile("lombok.*").asPredicate().negate().or(LombokClassNames.NON_NULL::equals))
        .filter(Pattern.compile(annotationToComparePattern()).asPredicate())
        .filter(Predicate.not(annotationsToIgnoreList()::contains))
        .toList();
      Collection<String> afterAnnotations = Arrays.stream(afterModifierList.getAnnotations())
        .map(PsiAnnotation::getQualifiedName)
        .filter(Pattern.compile(annotationToComparePattern()).asPredicate())
        .filter(Predicate.not(annotationsToIgnoreList()::contains))
        .toList();

      assertTrue("Annotations are different for " + afterModifierList.getParent() + ": " + beforeAnnotations + "/" + afterAnnotations,
                 beforeAnnotations.size() == afterAnnotations.size()
                 && beforeAnnotations.containsAll(afterAnnotations)
                 && afterAnnotations.containsAll(beforeAnnotations));

      // compare annotations parameter list
      for (PsiAnnotation beforeAnnotation : beforeModifierList.getAnnotations()) {
        String qualifiedName = beforeAnnotation.getQualifiedName();
        PsiAnnotation afterAnnotation = afterModifierList.findAnnotation(qualifiedName);
        if (null != afterAnnotation) {
          Map<String, String> beforeParameter = Stream.of(beforeAnnotation.getParameterList().getAttributes())
            .collect(Collectors.toMap(PsiNameValuePair::getAttributeName, p->p.getValue().getText()));
          Map<String, String> afterParameter = Stream.of(afterAnnotation.getParameterList().getAttributes())
            .collect(Collectors.toMap(PsiNameValuePair::getAttributeName, p->p.getValue().getText()));
          assertEquals("Annotation parameter are not same for " + qualifiedName, afterParameter, beforeParameter);
        }
      }
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
          assertEquals("Method: (" + afterMethod.getName() + ") in Class:" + afterClass.getName() + " different", text2, text1);
        }
      }
      else {
        if (null != afterMethodBody) {
          fail("MethodCodeBlocks is null: Method: (" + beforeMethod.getName() + ") Class:" + beforeClass.getName());
        }
      }
    }
  }

  private static Collection<PsiMethod> filterMethods(PsiMethod[] beforeMethods, PsiMethod compareMethod) {
    Collection<PsiMethod> result = new ArrayList<>();
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
  private static Collection<String> mapToTypeString(PsiParameterList compareMethodParameterList) {
    Collection<String> result = new ArrayList<>();
    final PsiParameter[] compareMethodParameterListParameters = compareMethodParameterList.getParameters();
    for (PsiParameter compareMethodParameterListParameter : compareMethodParameterListParameters) {
      result.add(stripJavaLang(compareMethodParameterListParameter.getType().getCanonicalText()));
    }
    return result;
  }

  private static String[] toList(PsiNamedElement[] beforeMethods) {
    return Arrays.stream(beforeMethods).map(PsiNamedElement::getName)
      .filter(java.util.Objects::isNull).sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new);
  }

  private static void compareThrows(PsiReferenceList beforeThrows, PsiReferenceList afterThrows, PsiMethod psiMethod) {
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

    assertEquals("Constructor counts are different for Class: " + beforeClass.getName(), afterConstructors.length,
                 beforeConstructors.length);

    for (PsiMethod afterConstructor : afterConstructors) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = afterConstructor.getModifierList();
      final List<PsiType> afterConstructorParameterTypes =
        ContainerUtil.map(afterConstructor.getParameterList().getParameters(), PsiParameter::getType);

      for (PsiMethod beforeConstructor : beforeConstructors) {
        if (PsiElementUtil.methodMatches(beforeConstructor, null, null, afterConstructor.getName(), afterConstructorParameterTypes)) {
          final PsiModifierList intellijConstructorModifierList = beforeConstructor.getModifierList();
          compareModifiers(intellijConstructorModifierList, theirsFieldModifierList);

          compared = true;
          break;
        }
      }
      assertTrue("Constructors are not equal, Constructor: '" +
                 afterConstructor.getName() +
                 "' (with same parameters) not found in class : " +
                 beforeClass.getName(), compared);
    }
  }

  private static void compareContainingClasses(PsiClass intellij, PsiClass theirs) {
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
      compareAnnotations(intellijParameter.getModifierList(), theirsParameter.getModifierList());
    }
  }
}
