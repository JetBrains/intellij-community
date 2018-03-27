// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.NanoXmlUtil;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.annotations.*;

import java.io.File;
import java.util.*;
import java.util.jar.Attributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman
 * @since Jul 20, 2005
 */
public class TestNGUtil {
  public static final String TESTNG_GROUP_NAME = "TestNG";

  @SuppressWarnings("StaticNonFinalField") public static boolean hasDocTagsSupport = hasDocTagsSupport();

  private static boolean hasDocTagsSupport() {
    String testngJarPath = PathUtil.getJarPathForClass(Test.class);
    String version = JarUtil.getJarAttribute(new File(testngJarPath), Attributes.Name.IMPLEMENTATION_VERSION);
    return version != null && StringUtil.compareVersionNumbers(version, "5.12") <= 0;
  }

  public static final String TEST_ANNOTATION_FQN = Test.class.getName();
  public static final String FACTORY_ANNOTATION_FQN = Factory.class.getName();
  @SuppressWarnings("deprecation") public static final String[] CONFIG_ANNOTATIONS_FQN = {
      Configuration.class.getName(),
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

  @SuppressWarnings("deprecation") public static final String[] CONFIG_ANNOTATIONS_FQN_NO_TEST_LEVEL = {
      Configuration.class.getName(),
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

  @NonNls
  private static final String[] CONFIG_JAVADOC_TAGS = {
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

  private static final List<String> JUNIT_ANNOTATIONS =
      Arrays.asList("org.junit.Test", "org.junit.Before", "org.junit.BeforeClass", "org.junit.After", "org.junit.AfterClass");

  @NonNls
  private static final String SUITE_TAG_NAME = "suite";

  public static boolean hasConfig(PsiModifierListOwner element) {
    return hasConfig(element, CONFIG_ANNOTATIONS_FQN);
  }

  public static boolean hasConfig(PsiModifierListOwner element,
                                  String[] configAnnotationsFqn) {
    if (element instanceof PsiClass) {
      for (PsiMethod method : ((PsiClass)element).getAllMethods()) {
        if (isConfigMethod(method, configAnnotationsFqn)) return true;
      }
    } else {
      if (!(element instanceof PsiMethod)) return false;
      return isConfigMethod((PsiMethod)element, configAnnotationsFqn);
    }
    return false;
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
    if (method != null) {
      for (String fqn : CONFIG_ANNOTATIONS_FQN) {
        if (AnnotationUtil.isAnnotated(method, fqn, 0)) return fqn;
      }
    }
    return null;
  }

  public static boolean isTestNGAnnotation(PsiAnnotation annotation) {
    String qName = annotation.getQualifiedName();
    if (qName != null) {
      if (qName.equals(TEST_ANNOTATION_FQN)) return true;
      for (String qn : CONFIG_ANNOTATIONS_FQN) {
        if (qName.equals(qn)) return true;
      }
      if (qName.equals(TEST_ANNOTATION_FQN)) return true;
      for (String qn : CONFIG_ANNOTATIONS_FQN) {
        if (qName.equals(qn)) return true;
      }
    }
    return false;
  }

  public static boolean hasTest(PsiModifierListOwner element) {
    return CachedValuesManager.getCachedValue(element, () ->
      CachedValueProvider.Result.create(hasTest(element, true), PsiModificationTracker.MODIFICATION_COUNT));
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled) {
    return hasTest(element, true, checkDisabled, hasDocTagsSupport);
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkHierarchy, boolean checkDisabled, boolean checkJavadoc) {
    final PsiClass aClass;
    if (element instanceof PsiClass) {
      aClass = ((PsiClass)element);
    }
    else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod)element).getContainingClass();
    }
    else {
      aClass = null;
    }
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
    if (checkJavadoc && getTextJavaDoc((PsiDocCommentOwner)element) != null)
      return true;
    //now we check all methods for the test annotation
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) element;
      for (PsiMethod method : psiClass.getAllMethods()) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, TEST_ANNOTATION_FQN);
        if (annotation != null) {
          if (checkDisabled) {
            if (isDisabled(annotation)) continue;
          }
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
        final PsiAnnotation annotation = checkHierarchy ? AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton(TEST_ANNOTATION_FQN))
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
    final PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue("enabled");
    return attributeValue != null && attributeValue.textMatches("false");
  }

  private static PsiDocTag getTextJavaDoc(@NotNull final PsiDocCommentOwner element) {
    final PsiDocComment docComment = element.getDocComment();
    if (docComment != null) {
      return docComment.findTagByName("testng.test");
    }
    return null;
  }

  public static boolean isAnnotatedWithParameter(PsiAnnotation annotation, String parameter, Set<String> values) {
    final PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue(parameter);
    if (attributeValue != null) {
      Collection<String> matches = extractValuesFromParameter(attributeValue);
      for (String s : matches) {
        if (values.contains(s)) {
          return true;
        }
      }
    }
    return false;
  }

  public static Set<String> getAnnotationValues(String parameter, PsiClass... classes) {
    Map<String, Collection<String>> results = new HashMap<>();
    final HashSet<String> set = new HashSet<>();
    results.put(parameter, set);
    collectAnnotationValues(results, null, classes);
    return set;
  }

  /**
   * @return were javadoc params used
   */
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
      } else {
        values.addAll(extractAnnotationValuesFromJavaDoc(getTextJavaDoc(commentOwner), parameter));
      }
    }
  }

  private static Collection<String> extractAnnotationValuesFromJavaDoc(PsiDocTag tag, String parameter) {
    if (tag == null) return Collections.emptyList();
    Collection<String> results = new ArrayList<>();
    Matcher matcher = Pattern.compile("@testng.test(?:.*)" + parameter + "\\s*=\\s*\"(.*?)\".*").matcher(tag.getText());
    if (matcher.matches()) {
      String[] groups = matcher.group(1).split("[,\\s]");
      for (String group : groups) {
        final String trimmed = group.trim();
        if (trimmed.length() > 0) {
          results.add(trimmed);
        }
      }
    }
    return results;
  }

  private static Collection<String> extractValuesFromParameter(PsiAnnotationMemberValue value) {
    Collection<String> results = new ArrayList<>();
    if (value instanceof PsiArrayInitializerMemberValue) {
      for (PsiElement child : value.getChildren()) {
        if (child instanceof PsiLiteralExpression) {
          results.add((String) ((PsiLiteralExpression) child).getValue());
        }
      }
    } else {
      if (value instanceof PsiLiteralExpression) {
        results.add((String) ((PsiLiteralExpression) value).getValue());
      }
    }
    return results;
  }

  @Nullable
  public static PsiClass[] getAllTestClasses(final TestClassFilter filter, boolean sync) {
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
            indicator.setText2("Found test class " + ReadAction.compute(psiClass::getQualifiedName));
          }
          set.add(psiClass);
        }
      }
      holder[0] = set.toArray(new PsiClass[set.size()]);
    };
    if (sync) {
       ProgressManager.getInstance().runProcessWithProgressSynchronously(process, "Searching For Tests...", true, filter.getProject());
    }
    else {
       process.run();
    }
    return holder[0];
  }

  public static PsiAnnotation[] getTestNGAnnotations(PsiElement element) {
    PsiElementProcessor.CollectFilteredElements<PsiAnnotation> processor = new PsiElementProcessor.CollectFilteredElements<>(e -> {
      if (e instanceof PsiAnnotation) {
        String name = ((PsiAnnotation)e).getQualifiedName();
        if (name != null && name.startsWith("org.testng.annotations")) {
          return true;
        }
      }
      return false;
    });
    PsiTreeUtil.processElements(element, processor);
    return processor.toArray(PsiAnnotation.EMPTY_ARRAY);
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
                                        "TestNG will be added to module classpath",
                                        "Unable to Convert.",
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
    if (psiClass != null) {
      for (PsiMethod method : psiClass.getMethods()) {
        if (containsJunitAnnotations(method)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean containsJunitAnnotations(PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, JUNIT_ANNOTATIONS, 0);
  }

  public static boolean inheritsJUnitTestCase(PsiClass psiClass) {
    PsiClass current = psiClass;
    while (current != null) {
      PsiClass[] supers = current.getSupers();
      if (supers.length > 0) {
        PsiClass parent = supers[0];
        if ("junit.framework.TestCase".equals(parent.getQualifiedName())) return true;
        current = parent;
        //handle typo where class extends itself
        if (current == psiClass) return false;
      } else {
        current = null;
      }
    }
    return false;
  }

  public static boolean inheritsITestListener(@NotNull PsiClass psiClass) {
    final Project project = psiClass.getProject();
    final PsiClass aListenerClass = JavaPsiFacade.getInstance(project)
      .findClass(ITestNGListener.class.getName(), GlobalSearchScope.allScope(project));
    return aListenerClass != null && psiClass.isInheritor(aListenerClass, true);
  }

  public static boolean isTestngXML(final VirtualFile virtualFile) {
    if ("xml".equalsIgnoreCase(virtualFile.getExtension()) && virtualFile.isInLocalFileSystem() && virtualFile.isValid()) {
      final String result = NanoXmlUtil.parseHeader(virtualFile).getRootTagLocalName();
      if (result != null && result.equals(SUITE_TAG_NAME)) {
        return true;
      }
    }
    return false;
  }

  public static PsiClass getProviderClass(final PsiElement element, final PsiClass topLevelClass) {
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("dataProviderClass");
      if (value instanceof PsiClassObjectAccessExpression) {
        final PsiTypeElement operand = ((PsiClassObjectAccessExpression)value).getOperand();
        final PsiClass psiClass = PsiUtil.resolveClassInType(operand.getType());
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    return topLevelClass;
  }

  @Nullable
  public static Version detectVersion(Project project, @Nullable Module module) {
    if (module == null) return null;
    return CachedValuesManager.getManager(project).getCachedValue(module, () -> {
      Version version = null;
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      PsiClass aClass = psiFacade.findClass("org.testng.internal.Version",
                                            GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (aClass != null) {
        PsiField versionField = aClass.findFieldByName("VERSION", false);
        if (versionField != null) {
          PsiExpression initializer = versionField.getInitializer();
          if (initializer instanceof PsiLiteralExpression) {
            Object eval = ((PsiLiteralExpression)initializer).getValue();
            if (eval instanceof String) {
              version = Version.parseVersion((String)eval);
            }
          }
        }
      }
      return CachedValueProvider.Result.createSingleDependency(version, ProjectRootManager.getInstance(module.getProject()));
    });
  }
}