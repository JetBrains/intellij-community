package de.plushnikov.intellij.plugin;

import com.google.common.base.Objects;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.util.DumbIncompleteModeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;

/**
 * Base test case for testing that the Lombok plugin parses the Lombok annotations correctly.
 */
public abstract class AbstractLombokParsingTestCase extends AbstractLombokLightCodeInsightTestCase {

  private static final Logger LOG = Logger.getInstance(AbstractLombokParsingTestCase.class);

  @Nullable
  protected ModeRunnerType myRunnerType;

  @NotNull
  protected List<ModeRunnerType> modes() {
    ArrayList<ModeRunnerType> types = new ArrayList<>();
    if (Registry.is("lombok.incomplete.mode.enabled", false)) {
      types.add(ModeRunnerType.INCOMPLETE);
    }
    if (Registry.is("lombok.dumb.mode.enabled", false)) {
      types.add(ModeRunnerType.DUMB);
    }
    types.add(ModeRunnerType.NORMAL);
    return types;
  }

  /**
   * All tests are run in three modes: normal, incomplete and dumb mode.
   * If it is necessary to skip some of them, use @SkipMode
   */
  @Override
  protected void runBare(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    String testName = getName();

    for (ModeRunnerType value : modes()) {
      myRunnerType = value;
      try {
        LOG.info("Method: " + testName + " starts with " + value);
        super.runBare(testRunnable);
        LOG.info("Method: " + testName + " finish with " + value);
      }
      catch (Throwable e) {
        LOG.warn("Method: " + testName + "failed for " + value);
        throw e;
      }
    }
  }

  /**
   * Represents the different modes for tests:
   * <ul>
   *     <li>NORMAL - uses to test mode when downloading and indexing are finished (indexes are available)</li>
   *     <li>DUMB - uses to test dumb mode, indexing is in progress (all libraries are downloaded, indexes are not available)</li>
   *     <li>INCOMPLETE - uses to test incomplete mode (libraries are being downloaded, indexes are available, but they don't contain all data)</li>
   * </ul>
   *
   * @see DumbService
   * @see IncompleteDependenciesService
   */
  public enum ModeRunnerType {
    INCOMPLETE,
    DUMB,
    NORMAL
  }

  protected boolean shouldCompareAnnotations() {
    return !".*".equals(annotationToComparePattern());
  }

  @Override
  protected final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return (myRunnerType == ModeRunnerType.INCOMPLETE) ? getProjectDescriptorForIncompleteMode() : getProjectDescriptorForNormalMode();
  }

  /**
   * @return the project descriptor for incomplete mode
   *
   * @see ModeRunnerType
   */
  protected @NotNull LightProjectDescriptor getProjectDescriptorForIncompleteMode() {
    return LombokTestUtil.WITHOUT_LOMBOK_JAVA_21_DESCRIPTOR;
  }

  /**
   * @return the project descriptor for normal mode
   *
   * @see ModeRunnerType
   */
  protected @NotNull LightProjectDescriptor getProjectDescriptorForNormalMode() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  protected String annotationToComparePattern() {
    return ".*";
  }

  protected Collection<String> annotationsToIgnoreList() {
    return Set.of("java.lang.SuppressWarnings", "java.lang.Override", "com.fasterxml.jackson.databind.annotation.JsonDeserialize");
  }

  protected boolean shouldCompareCodeBlocks() {
    return true;
  }

  public void doTest() {
    doTest(false);
  }

  public final void doTest(final boolean lowercaseFirstLetter) {
    PsiManager.getInstance(getProject()).dropPsiCaches();
    if (myRunnerType == ModeRunnerType.DUMB) {
      DumbModeTestUtils.runInDumbModeSynchronously(getProject(),
                                                   () -> compareFiles(lowercaseFirstLetter));
    }
    else if (myRunnerType == ModeRunnerType.INCOMPLETE) {
      IncompleteDependenciesService service = getProject().getService(IncompleteDependenciesService.class);
      try (var ignored = asAutoCloseable(WriteAction.compute(() -> service.enterIncompleteState(this)))) {
        compareFiles(lowercaseFirstLetter);
      }
    }
    else {
      compareFiles(lowercaseFirstLetter);
    }
  }

  protected void compareFiles(boolean lowercaseFirstLetter) {
    String testName = getTestName(lowercaseFirstLetter);
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
    if (beforeFieldModifierList != null && afterFieldModifierList != null) {
      compareModifiers(beforeFieldModifierList, afterFieldModifierList);
    }
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

          if (beforeFieldModifierList != null && afterFieldModifierList != null) {
            compareModifiers(beforeFieldModifierList, afterFieldModifierList);
          }
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

  private void compareType(PsiType beforeType, PsiType afterType, PomNamedTarget whereTarget) {
    if (null != beforeType && null != afterType) {
      DumbService.getInstance(getProject()).runWithAlternativeResolveEnabled(() -> {
        final String afterText = stripJavaLang(afterType.getCanonicalText());
        final String beforeText = stripJavaLang(beforeType.getCanonicalText());
        assertEquals(String.format("Types are not equal for element: %s", whereTarget.getName()), afterText, beforeText);
      });
    }
  }

  private static String stripJavaLang(String canonicalText) {
    return StringUtil.trimStart(canonicalText, "java.lang.");
  }

  private void compareModifiers(@NotNull PsiModifierList beforeModifierList, @NotNull PsiModifierList afterModifierList) {
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
        .map(an -> getAnnotationQualifiedName(an))
        .filter(Pattern.compile("lombok.*").asPredicate().negate().or(LombokClassNames.NON_NULL::equals))
        .filter(Pattern.compile(annotationToComparePattern()).asPredicate())
        .filter(Predicate.not(annotationsToIgnoreList()::contains))
        .toList();
      Collection<String> afterAnnotations = Arrays.stream(afterModifierList.getAnnotations())
        .map(an -> getAnnotationQualifiedName(an))
        .filter(Pattern.compile(annotationToComparePattern()).asPredicate())
        .filter(Predicate.not(annotationsToIgnoreList()::contains))
        .toList();

      if (DumbIncompleteModeUtil.isIncompleteMode(beforeModifierList.getProject())) {
        //In this case, it is impossible to resolve, and even if there are annotations we can't guess their fqn correctly.
        //Let's check one by one considering import statements
        for (String annotation : afterAnnotations) {
          assertTrue("For " + afterModifierList.getParent() + " " + beforeAnnotations + " doesn't contain the annotation: " + annotation,
                     ContainerUtil.or(beforeModifierList.getAnnotations(),
                                      an -> PsiAnnotationSearchUtil.checkAnnotationHasOneOfFQNs(an, Set.of(annotation))));
        }
      }
      else {
        assertTrue("Annotations are different for " + afterModifierList.getParent() + ": " + beforeAnnotations + "/" + afterAnnotations,
                   beforeAnnotations.size() == afterAnnotations.size()
                   && beforeAnnotations.containsAll(afterAnnotations)
                   && afterAnnotations.containsAll(beforeAnnotations));
      }

      // compare annotations parameter list
      for (PsiAnnotation beforeAnnotation : beforeModifierList.getAnnotations()) {
        String qualifiedName = getAnnotationQualifiedName(beforeAnnotation);
        PsiAnnotation afterAnnotation = findAnnotation(afterModifierList, qualifiedName);
        if (null != afterAnnotation) {
          Map<String, String> beforeParameter = Stream.of(beforeAnnotation.getParameterList().getAttributes())
            .collect(Collectors.toMap(PsiNameValuePair::getAttributeName, p -> p.getValue().getText()));
          Map<String, String> afterParameter = Stream.of(afterAnnotation.getParameterList().getAttributes())
            .collect(Collectors.toMap(PsiNameValuePair::getAttributeName, p -> p.getValue().getText()));
          assertEquals("Annotation parameter are not same for " + qualifiedName, afterParameter, beforeParameter);
        }
      }
    }
  }

  private static @Nullable PsiAnnotation findAnnotation(PsiModifierList modifierList, String qualifiedName) {
    return DumbService.getInstance(modifierList.getProject())
      .computeWithAlternativeResolveEnabled(() -> modifierList.findAnnotation(qualifiedName));
  }

  private static @NotNull String getAnnotationQualifiedName(@NotNull PsiAnnotation annotation) {
    return DumbService.getInstance(annotation.getProject())
      .computeWithAlternativeResolveEnabled(() -> annotation.getQualifiedName());
  }

  private void compareMethods(PsiClass beforeClass, PsiClass afterClass) {
    PsiMethod[] beforeMethods = beforeClass.getMethods();
    PsiMethod[] afterMethods = afterClass.getMethods();

    assertEquals("Methods are different for Class: " + beforeClass.getName(),
                 Arrays.toString(toList(afterMethods)), Arrays.toString(toList(beforeMethods)));

    for (PsiMethod afterMethod : afterMethods) {

      final Collection<PsiMethod> matchedMethods = filterMethods(beforeMethods, afterMethod);
      if (matchedMethods.isEmpty()) {
        fail("Method names are not equal, Method: " +
             afterMethod.getPresentation().getPresentableText() +
             " not found in class : " +
             beforeClass.getName());
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
    DumbService dumbService = DumbService.getInstance(compareMethodParameterList.getProject());
    for (PsiParameter compareMethodParameterListParameter : compareMethodParameterListParameters) {
      PsiType type = compareMethodParameterListParameter.getType();
      result.add(stripJavaLang(dumbService.computeWithAlternativeResolveEnabled(() -> type.getCanonicalText())));
    }
    return result;
  }

  private static String[] toList(PsiNamedElement[] beforeMethods) {
    return Arrays.stream(beforeMethods).map(PsiNamedElement::getName)
      .filter(java.util.Objects::nonNull).sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new);
  }

  private static void compareThrows(PsiReferenceList beforeThrows, PsiReferenceList afterThrows, PsiMethod psiMethod) {
    PsiClassType[] beforeTypes = beforeThrows.getReferencedTypes();
    PsiClassType[] afterTypes = afterThrows.getReferencedTypes();

    assertEquals("Throws counts are different for Method :" + psiMethod.getName(), beforeTypes.length, afterTypes.length);
    DumbService dumbService = DumbService.getInstance(psiMethod.getProject());
    for (PsiClassType beforeType : beforeTypes) {
      boolean found = false;
      for (PsiClassType afterType : afterTypes) {
        boolean equals = dumbService.computeWithAlternativeResolveEnabled(() -> beforeType.equals(afterType));
        if (equals) {
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
        boolean methodMatches = DumbService.getInstance(getProject()).computeWithAlternativeResolveEnabled(
          () -> PsiElementUtil.methodMatches(beforeConstructor, null, null, afterConstructor.getName(), afterConstructorParameterTypes));
        if (methodMatches) {
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
