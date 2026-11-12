package de.plushnikov.intellij.plugin;

import com.google.common.base.Objects;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomNamedTarget;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.util.DumbIncompleteModeUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiElementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The fixture of the Lombok parsing tests. Create it with {@code lombokParsingFixture} or {@code lombokConfigSystemFixture}.
 * <p>
 * A parsing test compares the PSI that Lombok augments in {@code before/<test name>.java} with the PSI of {@code after/<test name>.java}.
 * A {@code $} in the test name is a directory separator.
 * Declare each test with {@link LombokParsingTest}, so that it runs once in each {@link ModeRunnerType}.
 * A plain {@code @Test} runs in the {@link ModeRunnerType#NORMAL} mode only.
 */
@TestOnly
public final class LombokParsingFixture {

  private static final Logger LOG = Logger.getInstance(LombokParsingFixture.class);
  private static final Collection<String> ANNOTATIONS_TO_IGNORE =
    Set.of("java.lang.SuppressWarnings", "java.lang.Override", "com.fasterxml.jackson.databind.annotation.JsonDeserialize");

  private final @NotNull LightJavaCodeInsightTestFixture myFixture;
  private final @NotNull ModeRunnerType myRunnerType;
  private final @NotNull LombokParsingOptions myOptions;
  private final boolean myConfigSystem;

  LombokParsingFixture(@NotNull LightJavaCodeInsightTestFixture fixture,
                       @NotNull ModeRunnerType runnerType,
                       @NotNull LombokParsingOptions options,
                       boolean configSystem) {
    myFixture = fixture;
    myRunnerType = runnerType;
    myOptions = options;
    myConfigSystem = configSystem;
  }

  /**
   * Returns the modes of a parsing test: {@link ModeRunnerType#INCOMPLETE} and {@link ModeRunnerType#DUMB} when their registry keys
   * are on, and {@link ModeRunnerType#NORMAL}. {@link LombokParsingTest} runs a test in these modes.
   */
  static @NotNull List<ModeRunnerType> modes() {
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
   * Represents the different modes for tests:
   * <ul>
   *     <li>NORMAL - uses to test mode when downloading and indexing are finished (indexes are available)</li>
   *     <li>DUMB - uses to test dumb mode, indexing is in progress (all libraries are downloaded, indexes are not available)</li>
   *     <li>INCOMPLETE - uses to test incomplete mode (libraries are being downloaded, indexes are available, but they don't contain all data)</li>
   * </ul>
   * In the incomplete mode, the module has the descriptor of {@link LombokParsingOptions#incompleteDescriptor}.
   *
   * @see DumbService
   * @see IncompleteDependenciesService
   */
  public enum ModeRunnerType {
    INCOMPLETE,
    DUMB,
    NORMAL
  }

  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return myFixture;
  }

  public @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return myFixture.getTestName(lowercaseFirstLetter);
  }

  private boolean shouldCompareAnnotations() {
    Boolean compareAnnotations = myOptions.getCompareAnnotations();
    return compareAnnotations != null ? compareAnnotations : !".*".equals(myOptions.getAnnotationToComparePattern());
  }

  /**
   * Compares the files of the test. A config system fixture lowercases the first letter of the test name, a parsing fixture does not.
   */
  public void doTest() {
    doTest(myConfigSystem);
  }

  public void doTest(final boolean lowercaseFirstLetter) {
    Project project = myFixture.getProject();
    EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(project).dropPsiCaches());
    if (myRunnerType == ModeRunnerType.DUMB) {
      DumbModeTestUtils.runInDumbModeSynchronously(project,
                                                   () -> compareFiles(lowercaseFirstLetter));
    }
    else if (myRunnerType == ModeRunnerType.INCOMPLETE) {
      IncompleteDependenciesService service = project.getService(IncompleteDependenciesService.class);
      IncompleteDependenciesService.IncompleteDependenciesAccessToken token =
        EdtTestUtil.runInEdtAndGet(() -> WriteAction.compute(() -> service.enterIncompleteState(this)));
      try {
        compareFiles(lowercaseFirstLetter);
      }
      finally {
        EdtTestUtil.runInEdtAndWait(() -> asAutoCloseable(token).close());
      }
    }
    else {
      compareFiles(lowercaseFirstLetter);
    }
  }

  private void compareFiles(boolean lowercaseFirstLetter) {
    if (myConfigSystem) {
      final String fullFileName = getTestName(lowercaseFirstLetter).replace('$', '/') + ".java";
      final int lastIndexOf = fullFileName.lastIndexOf('/');
      final String subPath = fullFileName.substring(0, lastIndexOf);
      final String fileName = fullFileName.substring(lastIndexOf + 1);

      myFixture.copyFileToProject(subPath + "/before/lombok.config", subPath + "/before/lombok.config");

      doTest(subPath + "/before/" + fileName, subPath + "/after/" + fileName);
      return;
    }
    String testName = getTestName(lowercaseFirstLetter);
    PsiJavaFile beforeLombokFile = getPsiJavaFile(testName, "before");
    PsiJavaFile afterDelombokFile = getPsiJavaFile(testName, "after");
    ReadAction.runBlocking(() -> compareFiles(beforeLombokFile, afterDelombokFile));
  }

  /**
   * Loads {@code afterFileName} and then {@code beforeFileName} of the test data, and compares them.
   */
  public void doTest(final String beforeFileName, final String afterFileName) {
    final PsiFile psiDelombokFile = LombokTestUtil.loadToPsiFile(myFixture, afterFileName);
    final PsiFile psiLombokFile = LombokTestUtil.loadToPsiFile(myFixture, beforeFileName);

    if (!(psiLombokFile instanceof PsiJavaFile) || !(psiDelombokFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }

    ReadAction.runBlocking(() -> compareFiles((PsiJavaFile)psiLombokFile, (PsiJavaFile)psiDelombokFile));
  }

  @NotNull
  private PsiJavaFile getPsiJavaFile(String testName, String type) {
    final String fileName = testName.replace('$', '/') + ".java";
    final String filePath = type + "/" + fileName;
    final PsiFile psiFile = LombokTestUtil.loadToPsiFile(myFixture, filePath);
    if (!(psiFile instanceof PsiJavaFile)) {
      fail("The test file type is not supported");
    }
    return (PsiJavaFile)psiFile;
  }

  private void compareFiles(PsiJavaFile beforeLombokFile, PsiJavaFile afterDelombokFile) {
    PsiClass[] beforeClasses = beforeLombokFile.getClasses();
    PsiClass[] afterClasses = afterDelombokFile.getClasses();

    compareClasses(beforeClasses, afterClasses);
  }

  private void compareClasses(PsiClass[] beforeClasses, PsiClass[] afterClasses) {
    String before = "Before classes: " + Arrays.toString(beforeClasses);
    String after = "After classes: " + Arrays.toString(afterClasses);

    assertEquals(afterClasses.length, beforeClasses.length, "Class counts are different " + before + " <> " + after);

    for (PsiClass afterClass : afterClasses) {
      boolean compared = false;
      for (PsiClass beforeClass : beforeClasses) {
        if (Objects.equal(afterClass.getName(), beforeClass.getName())) {
          compareTwoClasses(beforeClass, afterClass);
          compared = true;
        }
      }
      assertTrue(compared, "Class names are not equal, class (" + afterClass.getName() + ") not found");
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

    assertEquals(Arrays.toString(toList(afterClassFields)), Arrays.toString(toList(beforeClassFields)), "Field are different for Class: " + beforeClass.getName());

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
      assertTrue(compared, "Fieldnames are not equal, Field (" + afterField.getName() + ") not found");
    }
  }

  private static void compareInitializers(PsiExpression beforeInitializer, PsiExpression afterInitializer) {
    String beforeInitializerText = null == beforeInitializer ? "" : beforeInitializer.getText();
    String afterInitializerText = null == afterInitializer ? "" : afterInitializer.getText();
    assertEquals(afterInitializerText, beforeInitializerText, "Initializers are not equals ");
  }

  private void compareType(PsiType beforeType, PsiType afterType, PomNamedTarget whereTarget) {
    if (null != beforeType && null != afterType) {
      DumbService.getInstance(myFixture.getProject()).runWithAlternativeResolveEnabled(() -> {
        final String afterText = stripJavaLang(afterType.getCanonicalText());
        final String beforeText = stripJavaLang(beforeType.getCanonicalText());
        assertEquals(afterText, beforeText, String.format("Types are not equal for element: %s", whereTarget.getName()));
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
        .filter(Pattern.compile(myOptions.getAnnotationToComparePattern()).asPredicate())
        .filter(Predicate.not(ANNOTATIONS_TO_IGNORE::contains))
        .toList();
      Collection<String> afterAnnotations = Arrays.stream(afterModifierList.getAnnotations())
        .map(an -> getAnnotationQualifiedName(an))
        .filter(Pattern.compile(myOptions.getAnnotationToComparePattern()).asPredicate())
        .filter(Predicate.not(ANNOTATIONS_TO_IGNORE::contains))
        .toList();

      if (DumbIncompleteModeUtil.isIncompleteMode(beforeModifierList.getProject())) {
        //In this case, it is impossible to resolve, and even if there are annotations we can't guess their fqn correctly.
        //Let's check one by one considering import statements
        for (String annotation : afterAnnotations) {
          assertTrue(ContainerUtil.or(beforeModifierList.getAnnotations(),
                                      an -> PsiAnnotationSearchUtil.checkAnnotationHasOneOfFQNs(an, Set.of(annotation))), "For " + afterModifierList.getParent() + " " + beforeAnnotations + " doesn't contain the annotation: " + annotation);
        }
      }
      else {
        assertTrue(beforeAnnotations.size() == afterAnnotations.size()
                   && beforeAnnotations.containsAll(afterAnnotations)
                   && afterAnnotations.containsAll(beforeAnnotations), "Annotations are different for " + afterModifierList.getParent() + ": " + beforeAnnotations + "/" + afterAnnotations);
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
          assertEquals(afterParameter, beforeParameter, "Annotation parameter are not same for " + qualifiedName);
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

    assertEquals(Arrays.toString(toList(afterMethods)), Arrays.toString(toList(beforeMethods)), "Methods are different for Class: " + beforeClass.getName());

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

    if (myOptions.isCompareCodeBlocks()) {
      final PsiCodeBlock beforeMethodBody = beforeMethod.getBody();
      final PsiCodeBlock afterMethodBody = afterMethod.getBody();
      if (null != beforeMethodBody && null != afterMethodBody) {

        boolean codeBlocksAreEqual = beforeMethodBody.textMatches(afterMethodBody);
        if (!codeBlocksAreEqual) {
          String text1 = beforeMethodBody.getText().replaceAll("java\\.lang\\.", "").replaceAll("\\s+", "");
          String text2 = afterMethodBody.getText().replaceAll("java\\.lang\\.", "").replaceAll("\\s+", "");
          assertEquals(text2, text1, "Method: (" + afterMethod.getName() + ") in Class:" + afterClass.getName() + " different");
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

    assertEquals(beforeTypes.length, afterTypes.length, "Throws counts are different for Method :" + psiMethod.getName());
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
      assertTrue(found, "Expected throw: " + beforeType.getClassName() + " not found on " + psiMethod.getName());
    }
  }

  private void compareConstructors(PsiClass beforeClass, PsiClass afterClass) {
    PsiMethod[] beforeConstructors = beforeClass.getConstructors();
    PsiMethod[] afterConstructors = afterClass.getConstructors();

    LOG.debug("IntelliJ constructors for class " + beforeClass.getName() + ": " + Arrays.toString(beforeConstructors));
    LOG.debug("Theirs constructors for class " + afterClass.getName() + ": " + Arrays.toString(afterConstructors));

    assertEquals(afterConstructors.length,
                 beforeConstructors.length, "Constructor counts are different for Class: " + beforeClass.getName());

    for (PsiMethod afterConstructor : afterConstructors) {
      boolean compared = false;
      final PsiModifierList theirsFieldModifierList = afterConstructor.getModifierList();
      final List<PsiType> afterConstructorParameterTypes =
        ContainerUtil.map(afterConstructor.getParameterList().getParameters(), PsiParameter::getType);

      for (PsiMethod beforeConstructor : beforeConstructors) {
        boolean methodMatches = DumbService.getInstance(myFixture.getProject()).computeWithAlternativeResolveEnabled(
          () -> PsiElementUtil.methodMatches(beforeConstructor, null, null, afterConstructor.getName(), afterConstructorParameterTypes));
        if (methodMatches) {
          final PsiModifierList intellijConstructorModifierList = beforeConstructor.getModifierList();
          compareModifiers(intellijConstructorModifierList, theirsFieldModifierList);

          compared = true;
          break;
        }
      }
      assertTrue(compared, "Constructors are not equal, Constructor: '" +
                 afterConstructor.getName() +
                 "' (with same parameters) not found in class : " +
                 beforeClass.getName());
    }
  }

  private static void compareContainingClasses(PsiClass intellij, PsiClass theirs) {
    PsiClass intellijContainingClass = intellij.getContainingClass();
    PsiClass theirsContainingClass = theirs.getContainingClass();

    String intellijContainingClassName = intellijContainingClass == null ? null : intellijContainingClass.toString();
    String theirsContainingClassName = theirsContainingClass == null ? null : theirsContainingClass.toString();

    LOG.debug("IntelliJ containing class for class " + intellij.getName() + ": " + intellijContainingClassName);
    LOG.debug("Theirs containing class for class " + theirs.getName() + ": " + theirsContainingClassName);

    assertEquals(intellijContainingClassName, theirsContainingClassName, "Containing classes different for class: " + intellij.getName());
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
