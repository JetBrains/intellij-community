// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.xml.NanoXmlUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.testng.Assert;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman
 */
public final class TestNGUtil {
  public static final String TESTNG_GROUP_NAME = "TestNG";

  @SuppressWarnings("StaticNonFinalField") public static boolean hasDocTagsSupport = hasDocTagsSupport();

  private static boolean hasDocTagsSupport() {
    String testngJarPath = PathUtil.getJarPathForClass(Test.class);
    String version = JarUtil.getJarAttribute(new File(testngJarPath), Attributes.Name.IMPLEMENTATION_VERSION);
    return version != null && StringUtil.compareVersionNumbers(version, "5.12") <= 0;
  }

  public static final String MAVEN_TEST_NG = "org.testng:testng";
  public static final String TEST_ANNOTATION_FQN = Test.class.getName();
  public static final String TESTNG_PACKAGE = "org.testng";
  public static final String FACTORY_ANNOTATION_FQN = Factory.class.getName();
  public static final String[] CONFIG_ANNOTATIONS_FQN = {
    "org.testng.annotations.Configuration",
    Factory.class.getName(),
    ObjectFactory.class.getName(),
    DataProvider.class.getName(),
    BeforeClass.class.getName(),
    BeforeGroups.class.getName(),
    BeforeMethod.class.getName(),
    BeforeSuite.class.getName(),
    BeforeTest.class.getName(),
    AfterClass.class.getName(),
    AfterGroups.class.getName(),
    AfterMethod.class.getName(),
    AfterSuite.class.getName(),
    AfterTest.class.getName()
  };

  public static final String[] CONFIG_ANNOTATIONS_FQN_NO_TEST_LEVEL = {
    "org.testng.annotations.Configuration",
    Factory.class.getName(),
    ObjectFactory.class.getName(),
    BeforeClass.class.getName(),
    BeforeGroups.class.getName(),
    BeforeSuite.class.getName(),
    BeforeTest.class.getName(),
    AfterClass.class.getName(),
    AfterGroups.class.getName(),
    AfterSuite.class.getName(),
    AfterTest.class.getName()
  };

  private static final @NonNls String[] CONFIG_JAVADOC_TAGS = {
    "testng.configuration",
    "testng.before-class",
    "testng.before-groups",
    "testng.before-method",
    "testng.before-suite",
    "testng.before-test",
    "testng.after-class",
    "testng.after-groups",
    "testng.after-method",
    "testng.after-suite",
    "testng.after-test"
  };

  private static final List<String> JUNIT_ANNOTATIONS = List.of(
    "org.junit.Test",
    "org.junit.Before",
    "org.junit.BeforeClass",
    "org.junit.After",
    "org.junit.AfterClass"
  );

  private static final @NonNls String SUITE_TAG_NAME = "suite";

  public static final String DATA_PROVIDER_ATTRIBUTE = "dataProvider";
  public static final String DATA_PROVIDER_CLASS_ATTRIBUTE = "dataProviderClass";

  public static boolean hasConfig(PsiModifierListOwner element) {
    return hasConfig(element, CONFIG_ANNOTATIONS_FQN);
  }

  public static boolean hasConfig(PsiModifierListOwner element,
                                  String[] configAnnotationsFqn) {
    return switch (element) {
      case PsiClass psiClass -> ContainerUtil.exists(psiClass.getAllMethods(), method -> isConfigMethod(method, configAnnotationsFqn));
      case PsiMethod psiMethod -> isConfigMethod(psiMethod, configAnnotationsFqn);
      case null, default -> false;
    };
  }

  private static boolean isConfigMethod(PsiMethod method, String[] configAnnotationsFqn) {
    for (String fqn : configAnnotationsFqn) {
      if (AnnotationUtil.isAnnotated(method, fqn, 0)) return true;
    }

    if (hasDocTagsSupport) {
      final PsiDocComment comment = method.getDocComment();
      if (comment != null) {
        for (String javadocTag : CONFIG_JAVADOC_TAGS) {
          if (comment.findTagByName(javadocTag) != null) return true;
        }
      }
    }
    return false;
  }

  public static String getConfigAnnotation(PsiMethod method) {
    if (method == null) return null;
    return ContainerUtil.find(CONFIG_ANNOTATIONS_FQN, fqn -> AnnotationUtil.isAnnotated(method, fqn, 0));
  }

  public static boolean hasTest(PsiModifierListOwner element) {
    return CachedValuesManager.getCachedValue(element, () ->
      CachedValueProvider.Result.create(hasTest(element, true), PsiModificationTracker.MODIFICATION_COUNT));
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled) {
    return hasTest(element, true, checkDisabled, hasDocTagsSupport);
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkHierarchy, boolean checkDisabled, boolean checkJavadoc) {
    final PsiClass aClass = switch (element) {
      case PsiClass psiClass -> psiClass;
      case PsiMethod psiMethod -> psiMethod.getContainingClass();
      case null, default -> null;
    };
    if (aClass == null || !PsiClassUtil.isRunnableClass(aClass, true, false)) {
      return false;
    }

    //LanguageLevel effectiveLanguageLevel = element.getManager().getEffectiveLanguageLevel();
    //boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;
    boolean hasAnnotation = AnnotationUtil.isAnnotated(element, TEST_ANNOTATION_FQN, checkHierarchy ? AnnotationUtil.CHECK_HIERARCHY : 0);
    if (hasAnnotation) {
      if (checkDisabled) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, true, TEST_ANNOTATION_FQN);
        if (annotation != null) {
          if (isDisabled(annotation)) return false;
        }
      }
      return true;
    }
    if (checkJavadoc && getTextJavaDoc((PsiDocCommentOwner)element) != null) return true;
    //now we check all methods for the test annotation
    if (element instanceof PsiClass psiClass) {
      for (PsiMethod method : psiClass.getAllMethods()) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, TEST_ANNOTATION_FQN);
        if (annotation != null) {
          if (checkDisabled && isDisabled(annotation)) continue;
          return true;
        }
        if (AnnotationUtil.isAnnotated(method, FACTORY_ANNOTATION_FQN, 0)) return true;
        if (checkJavadoc && getTextJavaDoc(method) != null) return true;
      }
      return false;
    }
    else {
      //even if it has a global test, we ignore non-public and static methods
      if (!element.hasModifierProperty(PsiModifier.PUBLIC) ||
          element.hasModifierProperty(PsiModifier.STATIC)) {
        return false;
      }

      //if it's a method, we check if the class it's in has a global @Test annotation
      PsiClass psiClass = ((PsiMethod)element).getContainingClass();
      if (psiClass != null) {
        final PsiAnnotation annotation =
          checkHierarchy ? AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton(TEST_ANNOTATION_FQN))
                         : AnnotationUtil.findAnnotation(psiClass, true, TEST_ANNOTATION_FQN);
        if (annotation != null) {
          if (checkDisabled && isDisabled(annotation)) return false;
          return !hasConfig(element);
        }
        else if (checkJavadoc && getTextJavaDoc(psiClass) != null) return true;
      }
    }
    return false;
  }

  public static boolean isDisabled(PsiAnnotation annotation) {
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, "enabled");
    final PsiAnnotationMemberValue attributeValue = attribute != null ? attribute.getDetachedValue() : null;
    return attributeValue != null && attributeValue.textMatches("false");
  }

  private static PsiDocTag getTextJavaDoc(final @NotNull PsiDocCommentOwner element) {
    final PsiDocComment docComment = element.getDocComment();
    if (docComment != null) {
      return docComment.findTagByName("testng.test");
    }
    return null;
  }

  public static boolean isAnnotatedWithParameter(PsiAnnotation annotation, String parameter, Set<String> values) {
    final PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue(parameter);
    if (attributeValue == null) return false;
    Collection<String> matches = extractValuesFromParameter(attributeValue);
    return ContainerUtil.exists(matches, values::contains);
  }

  public static Set<String> getAnnotationValues(String parameter, PsiClass... classes) {
    Map<String, Collection<String>> results = new HashMap<>();
    final HashSet<String> set = new HashSet<>();
    results.put(parameter, set);
    collectAnnotationValues(results, null, classes);
    return set;
  }

  public static void collectAnnotationValues(final Map<String, Collection<String>> results, PsiMethod[] psiMethods, PsiClass... classes) {
    final Set<String> test = new HashSet<>(1);
    test.add(TEST_ANNOTATION_FQN);
    ContainerUtil.addAll(test, CONFIG_ANNOTATIONS_FQN);
    if (psiMethods != null) {
      for (final PsiMethod psiMethod : psiMethods) {
        ApplicationManager.getApplication().runReadAction(
          () -> appendAnnotationAttributeValues(results, AnnotationUtil.findAnnotation(psiMethod, test), psiMethod)
        );
      }
    }
    else {
      for (final PsiClass psiClass : classes) {
        ApplicationManager.getApplication().runReadAction(() -> {
          if (psiClass != null && hasTest(psiClass)) {
            appendAnnotationAttributeValues(results, AnnotationUtil.findAnnotation(psiClass, test), psiClass);
            PsiMethod[] methods = psiClass.getMethods();
            for (PsiMethod method : methods) {
              if (method != null) {
                appendAnnotationAttributeValues(results, AnnotationUtil.findAnnotation(method, test), method);
              }
            }
          }
        });
      }
    }
  }

  private static void appendAnnotationAttributeValues(final Map<String, Collection<String>> results,
                                                      final PsiAnnotation annotation,
                                                      final PsiDocCommentOwner commentOwner) {
    for (String parameter : results.keySet()) {
      final Collection<String> values = results.get(parameter);
      if (annotation != null) {
        final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(parameter);
        if (value != null) {
          values.addAll(extractValuesFromParameter(value));
        }
      }
      else {
        values.addAll(extractAnnotationValuesFromJavaDoc(getTextJavaDoc(commentOwner), parameter));
      }
    }
  }

  private static Collection<String> extractAnnotationValuesFromJavaDoc(PsiDocTag tag, String parameter) {
    if (tag == null) return Collections.emptyList();
    Matcher matcher = Pattern.compile("@testng.test(?:.*)" + parameter + "\\s*=\\s*\"(.*?)\".*").matcher(tag.getText());
    if (!matcher.matches()) return Collections.emptyList();
    String[] groups = matcher.group(1).split("[,\\s]");
    return Arrays.stream(groups).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private static Collection<String> extractValuesFromParameter(PsiAnnotationMemberValue value) {
    return JBIterable.from(AnnotationUtil.arrayAttributeValues(value))
      .filter(PsiLiteralExpression.class)
      .map(PsiLiteralExpression::getValue)
      .filter(String.class)
      .toList();
  }

  public static PsiClass @Nullable [] getAllTestClasses(final TestClassFilter filter, boolean sync) {
    final PsiClass[][] holder = new PsiClass[1][];
    final Runnable process = () -> {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      final Collection<PsiClass> set = new LinkedHashSet<>();
      final PsiManager manager = PsiManager.getInstance(filter.getProject());
      final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
      final GlobalSearchScope scope = projectScope.intersectWith(filter.getScope());
      for (final PsiClass psiClass : AllClassesSearch.search(scope, manager.getProject())) {
        if (filter.isAccepted(psiClass)) {
          if (indicator != null) {
            indicator.setText2(TestngBundle.message("testng.util.found.test.class", ReadAction.computeBlocking(psiClass::getQualifiedName)));
          }
          set.add(psiClass);
        }
      }
      holder[0] = set.toArray(PsiClass.EMPTY_ARRAY);
    };
    if (sync) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(process, TestngBundle.message("testng.util.searching.test.progress.title"), true,
                                             filter.getProject());
    }
    else {
      process.run();
    }
    return holder[0];
  }

  public static PsiAnnotation[] getTestNGAnnotations(PsiElement element) {
    return SyntaxTraverser.psiTraverser(element).filter(PsiAnnotation.class)
      .filter(anno -> {
        String name = anno.getQualifiedName();
        return name != null && name.startsWith("org.testng.annotations");
      }).toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  public static boolean isTestNGClass(PsiClass psiClass) {
    return hasTest(psiClass);
  }

  public static boolean checkTestNGInClasspath(PsiElement psiElement) {
    final Project project = psiElement.getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    if (JavaPsiFacade.getInstance(manager.getProject()).findClass(TestNG.class.getName(), psiElement.getResolveScope()) == null) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        if (Messages.showOkCancelDialog(psiElement.getProject(),
                                        TestngBundle.message("testng.util.will.be.added.to.module.classpath"),
                                        TestngBundle.message("testng.util.unable.to.convert"),
                                        Messages.getWarningIcon()) != Messages.OK) {
          return false;
        }
      }
      final Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
      if (module == null) return false;
      String url = VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(Assert.class)));
      ModuleRootModificationUtil.addModuleLibrary(module, url);
    }
    return true;
  }

  public static boolean containsJunitAnnotations(PsiClass psiClass) {
    if (psiClass == null) return false;
    return ContainerUtil.exists(psiClass.getMethods(), TestNGUtil::containsJunitAnnotations);
  }

  public static boolean containsJunitAnnotations(PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, JUNIT_ANNOTATIONS, 0);
  }

  public static boolean inheritsJUnitTestCase(PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, "junit.framework.TestCase");
  }

  public static boolean inheritsITestListener(@NotNull PsiClass psiClass) {
    final Project project = psiClass.getProject();
    final PsiClass aListenerClass = JavaPsiFacade.getInstance(project)
      .findClass(ITestNGListener.class.getName(), GlobalSearchScope.allScope(project));
    return aListenerClass != null && psiClass.isInheritor(aListenerClass, true);
  }

  public static boolean isTestngSuiteFile(final VirtualFile virtualFile) {
    if (virtualFile.isInLocalFileSystem() && virtualFile.isValid()) {
      String extension = virtualFile.getExtension();
      if ("xml".equalsIgnoreCase(extension)) {
        final String result = NanoXmlUtil.parseHeader(virtualFile).getRootTagLocalName();
        if (result != null && result.equals(SUITE_TAG_NAME)) {
          return true;
        }
      }
      else if ("yaml".equals(extension)) {
        return true;
      }
    }
    return false;
  }

  public static PsiClass @NotNull [] getProviderClasses(@NotNull final PsiElement element, @Nullable final PsiClass topLevelClass) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
    if (annotation == null) return topLevelClass != null ? new PsiClass[]{topLevelClass} : PsiClass.EMPTY_ARRAY;
    PsiAnnotationMemberValue value = extractDataProviderClass(annotation);
    List<PsiAnnotationMemberValue> values = (value == null)
                                            ? findDataProviderClass(PsiTreeUtil.getParentOfType(element, PsiMethod.class))
                                            : List.of(value);

    PsiClass[] result = values.stream()
      .filter(PsiClassObjectAccessExpression.class::isInstance)
      .map(PsiClassObjectAccessExpression.class::cast)
      .map(expression -> PsiUtil.resolveClassInType(expression.getOperand().getType()))
      .filter(Objects::nonNull)
      .toArray(PsiClass[]::new);

    return result.length != 0
           ? result
           : topLevelClass != null ? new PsiClass[]{topLevelClass} : PsiClass.EMPTY_ARRAY;
  }

  private static @Nullable PsiAnnotationMemberValue extractDataProviderClass(@NotNull PsiAnnotation annotation) {
    return TEST_ANNOTATION_FQN.equals(annotation.getQualifiedName())
           ? annotation.findDeclaredAttributeValue(DATA_PROVIDER_CLASS_ATTRIBUTE)
           : null;
  }

  private static List<PsiAnnotationMemberValue> findDataProviderClass(@Nullable PsiMethod method) {
    if (method == null) return List.of();
    PsiClass aClass = method.getContainingClass();

    List<PsiAnnotationMemberValue> result = new ArrayList<>();
    while (aClass != null && result.isEmpty()) { // find parent class with data provider class
      for (PsiAnnotation annotation : aClass.getAnnotations()) {
        PsiAnnotationMemberValue value = extractDataProviderClass(annotation);
        if (value != null) {
          result.add(value);
          break;
        }
      }
      aClass = aClass.getSuperClass();
    }

    if (isVersionOrGreaterThan(method.getProject(), ModuleUtilCore.findModuleForPsiElement(method), 7, 0, 0)) {
      aClass = method.getContainingClass();
      if (aClass == null) return List.of();
      for (PsiClass psiClass : ClassInheritorsSearch.search(aClass, aClass.getResolveScope(), true)) {
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
          PsiAnnotationMemberValue value = extractDataProviderClass(annotation);
          if (value != null) result.add(value);
        }
      }
    }

    return result;
  }

  /**
   * Returns whether the version is greater or equal to the one passed into this method.
   * Returns false when the version cannot be detected.
   * Check only works when the supplied version is <= 7.4.0, otherwise it will always return true.
   */
  public static boolean isVersionOrGreaterThan(@NotNull Project project,
                                               @Nullable Module module,
                                               @Range(from = 0, to = 7) int major,
                                               int minor,
                                               int bugfix) {
    if (module == null) return false;
    Version version = detectVersion(project, module);
    if (version == null) return false;
    if (version.major == Integer.MAX_VALUE) return true;
    return version.isOrGreaterThan(major, minor, bugfix);
  }

  private static @Nullable Version detectVersion(@NotNull Project project, @NotNull Module module) {
    return CachedValuesManager.getManager(project).getCachedValue(module, () -> {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      PsiClass aClass = psiFacade.findClass("org.testng.internal.Version",
                                            GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (aClass == null) return null;
      PsiField versionField = aClass.findFieldByName("VERSION", false);
      if (versionField == null) return null;

      String version = versionField.getInitializer() instanceof PsiLiteralExpression l && l.getValue() instanceof String v
                       ? v : String.valueOf(Integer.MAX_VALUE);
      return CachedValueProvider.Result.createSingleDependency(Version.parseVersion(version),
                                                               ProjectRootManager.getInstance(module.getProject()));
    });
  }
}