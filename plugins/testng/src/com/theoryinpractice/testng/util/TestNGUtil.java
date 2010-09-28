/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.NanoXmlUtil;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * @author Hani Suleiman Date: Jul 20, 2005 Time: 1:37:36 PM
 */
public class TestNGUtil
{
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  public static final String TESTNG_GROUP_NAME = "TestNG";

  public static boolean hasDocTagsSupport = hasDocTagsSupport();

  private static boolean hasDocTagsSupport() {
    final String testngJarPath = PathUtil.getJarPathForClass(Test.class);
    if (testngJarPath != null) {
      final VirtualFile testngjar = LocalFileSystem.getInstance().findFileByPath(testngJarPath);
      if (testngjar != null ) {
        try {
          final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(testngjar);
          if (jarRoot != null) {
            final ZipFile zipFile = JarFileSystem.getInstance().getJarFile(jarRoot);
            final String version = JarVersionDetectionUtil.detectJarVersion(zipFile);
            if (version != null && version.compareTo("5.12") > 0) {
              return false;
            }
          }
        }
        catch (IOException e) {
          return true;
        }
      }
    }
    return true;
  }

  public static final String TEST_ANNOTATION_FQN = Test.class.getName();
  public static final String[] CONFIG_ANNOTATIONS_FQN = {
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
  static final List<String> junitAnnotions =
      Arrays.asList("org.junit.Test", "org.junit.Before", "org.junit.BeforeClass", "org.junit.After", "org.junit.AfterClass");
  private static final Logger LOG = Logger.getInstance("#" + TestNGUtil.class.getName());
  @NonNls
  private static final String SUITE_TAG_NAME = "suite";

  public static boolean hasConfig(PsiModifierListOwner element) {
    PsiMethod[] methods;
    if (element instanceof PsiClass) {
      methods = ((PsiClass) element).getMethods();
    } else {
      methods = new PsiMethod[] {(PsiMethod) element};
    }

    for (PsiMethod method : methods) {
      for (String fqn : CONFIG_ANNOTATIONS_FQN) {
        if (AnnotationUtil.isAnnotated(method, fqn, false)) return true;
      }

      for (PsiElement child : method.getChildren()) {
        if (child instanceof PsiDocComment) {
          PsiDocComment doc = (PsiDocComment) child;
          for (String javadocTag : CONFIG_JAVADOC_TAGS) {
            if (doc.findTagByName(javadocTag) != null) return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isTestNGAnnotation(PsiAnnotation annotation) {
    String qName = annotation.getQualifiedName();
    if (qName.equals(TEST_ANNOTATION_FQN)) return true;
    for (String qn : CONFIG_ANNOTATIONS_FQN) {
      if (qName.equals(qn)) return true;
    }
    return false;
  }

  public static boolean hasTest(PsiModifierListOwner element) {
    return hasTest(element, true);
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled) {
    return hasTest(element, checkDisabled, hasDocTagsSupport);
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled, boolean checkJavadoc) {
    //LanguageLevel effectiveLanguageLevel = element.getManager().getEffectiveLanguageLevel();
    //boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;
    boolean hasAnnotation = AnnotationUtil.isAnnotated(element, TEST_ANNOTATION_FQN, false, true);
    if (hasAnnotation) {
      if (checkDisabled) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, true, TEST_ANNOTATION_FQN);
        assert annotation != null;
        PsiNameValuePair[] attribs = annotation.getParameterList().getAttributes();
        for (PsiNameValuePair attrib : attribs) {
          final String attribName = attrib.getName();
          final PsiAnnotationMemberValue attribValue = attrib.getValue();
          if (Comparing.strEqual(attribName, "enabled") && attribValue != null && attribValue.textMatches("false"))
            return false;
        }
      }
      return true;
    }
    if (element instanceof PsiDocCommentOwner && hasTestJavaDoc((PsiDocCommentOwner) element, checkJavadoc))
      return true;
    //now we check all methods for the test annotation
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) element;
      for (PsiMethod method : psiClass.getAllMethods()) {
        if (AnnotationUtil.isAnnotated(method, TEST_ANNOTATION_FQN, false, true)) return true;
        if (hasTestJavaDoc(method, checkJavadoc)) return true;
      }
    } else if (element instanceof PsiMethod) {
      //if it's a method, we check if the class it's in has a global @Test annotation
      PsiClass psiClass = ((PsiMethod)element).getContainingClass();
      if (psiClass != null) {
        if (AnnotationUtil.isAnnotated(psiClass, TEST_ANNOTATION_FQN, false, true)) {
          //even if it has a global test, we ignore private methods
          boolean isPrivate = element.hasModifierProperty(PsiModifier.PRIVATE);
          return !isPrivate;
        }
        if (hasTestJavaDoc(psiClass, checkJavadoc)) return true;
      }
    }
    return false;
  }

  private static boolean hasTestJavaDoc(@NotNull PsiDocCommentOwner element, final boolean checkJavadoc) {
    if (checkJavadoc) {
      return getTextJavaDoc(element) != null;
    }
    return false;
  }

  private static PsiDocTag getTextJavaDoc(@NotNull final PsiDocCommentOwner element) {
    final PsiDocComment docComment = element.getDocComment();
    if (docComment != null) {
      return docComment.findTagByName("testng.test");
    }
    return null;
  }

  /**
   * Ignore these, they cause an NPE inside of AnnotationUtil
   */
  private static boolean isBrokenPsiClass(PsiClass psiClass) {
    return (psiClass == null
        || psiClass instanceof PsiAnonymousClass
        || psiClass instanceof JspClass);
  }

  /**
   * Filter the specified collection of classes to return only ones that contain any of the specified values in the
   * specified annotation parameter. For example, this method can be used to return all classes that contain all tesng
   * annotations that are in the groups 'foo' or 'bar'.
   */
  public static Map<PsiClass, Collection<PsiMethod>> filterAnnotations(String parameter, Set<String> values, Collection<PsiClass> classes) {
    Map<PsiClass, Collection<PsiMethod>> results = new HashMap<PsiClass, Collection<PsiMethod>>();
    Set<String> test = new HashSet<String>(1);
    test.add(TEST_ANNOTATION_FQN);
    ContainerUtil.addAll(test, CONFIG_ANNOTATIONS_FQN);
    for (PsiClass psiClass : classes) {
      if (isBrokenPsiClass(psiClass)) continue;

      PsiAnnotation annotation;
      try {
        annotation = AnnotationUtil.findAnnotation(psiClass, test);
      }
      catch (Exception e) {
        LOGGER.error("Exception trying to findAnnotation on " + psiClass.getClass().getName() + ".\n\n" + e.getMessage());
        annotation = null;
      }
      if (annotation != null) {
        if (isAnnotatedWithParameter(annotation, parameter, values)) {
          results.put(psiClass, new LinkedHashSet<PsiMethod>());
        }
      }
      else {
        Collection<String> matches = extractAnnotationValuesFromJavaDoc(getTextJavaDoc(psiClass), parameter);
        for (String s : matches) {
          if (values.contains(s)) {
            results.put(psiClass, new LinkedHashSet<PsiMethod>());
            break;
          }
        }
      }

      //we already have the class, no need to look through its methods
      PsiMethod[] methods = psiClass.getMethods();
      for (PsiMethod method : methods) {
        if (method != null) {
          annotation = AnnotationUtil.findAnnotation(method, test);
          if (annotation != null) {
            if (isAnnotatedWithParameter(annotation, parameter, values)) {
              if (results.get(psiClass) == null) results.put(psiClass, new LinkedHashSet<PsiMethod>());
              results.get(psiClass).add(method);
            }
          }
          else {
            Collection<String> matches = extractAnnotationValuesFromJavaDoc(getTextJavaDoc(psiClass), parameter);
            for (String s : matches) {
              if (values.contains(s)) {
                results.get(psiClass).add(method);
              }
            }
          }
        }
      }
    }
    return results;
  }

  public static boolean isAnnotatedWithParameter(PsiAnnotation annotation, String parameter, Set<String> values) {
    PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair aPair : pair) {
      if (parameter.equals(aPair.getName())) {
        Collection<String> matches = extractValuesFromParameter(aPair);
        for (String s : matches) {
          if (values.contains(s)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static Set<String> getAnnotationValues(String parameter, PsiClass... classes) {
    Set<String> results = new HashSet<String>();
    collectAnnotationValues(results, parameter, null, classes);
    return results;
  }

  /**
   * @return were javadoc params used
   */
  public static void collectAnnotationValues(final Set<String> results, final String parameter, PsiMethod[] psiMethods, PsiClass... classes) {
    final Set<String> test = new HashSet<String>(1);
    test.add(TEST_ANNOTATION_FQN);
    ContainerUtil.addAll(test, CONFIG_ANNOTATIONS_FQN);
    if (psiMethods != null) {
      for (final PsiMethod psiMethod : psiMethods) {
        ApplicationManager.getApplication().runReadAction(
          new Runnable() {
            public void run() {
              appendAnnotationAttributeValues(parameter, results, AnnotationUtil.findAnnotation(psiMethod, test), psiMethod);
            }
          }
        );
      }
    }
    else {
      for (final PsiClass psiClass : classes) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (psiClass != null && hasTest(psiClass)) {
              appendAnnotationAttributeValues(parameter, results, AnnotationUtil.findAnnotation(psiClass, test), psiClass);
              PsiMethod[] methods = psiClass.getMethods();
              for (PsiMethod method : methods) {
                if (method != null) {
                  appendAnnotationAttributeValues(parameter, results, AnnotationUtil.findAnnotation(method, test), method);
                }
              }
            }
          }
        });
      }
    }
  }

  private static void appendAnnotationAttributeValues(final String parameter,
                                                      final Collection<String> results,
                                                      final PsiAnnotation annotation,
                                                      final PsiDocCommentOwner commentOwner) {
    if (annotation != null) {
      PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair aPair : pair) {
        if (parameter.equals(aPair.getName())) {
          results.addAll(extractValuesFromParameter(aPair));
        }
      }
    } else {
      results.addAll(extractAnnotationValuesFromJavaDoc(getTextJavaDoc(commentOwner), parameter));
    }
  }

  private static Collection<String> extractAnnotationValuesFromJavaDoc(PsiDocTag tag, String parameter) {
    if (tag == null) return Collections.emptyList();
    Collection<String> results = new ArrayList<String>();
    Matcher matcher = Pattern.compile("\\@testng.test(?:.*)" + parameter + "\\s*=\\s*\"(.*?)\".*").matcher(tag.getText());
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

  private static Collection<String> extractValuesFromParameter(PsiNameValuePair aPair) {
    Collection<String> results = new ArrayList<String>();
    PsiAnnotationMemberValue value = aPair.getValue();
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

  public static PsiClass[] getAllTestClasses(final TestClassFilter filter, boolean sync) {
    final PsiClass[][] holder = new PsiClass[1][];
    final Runnable process = new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

        final Collection<PsiClass> set = new HashSet<PsiClass>();
        PsiManager manager = PsiManager.getInstance(filter.getProject());
        GlobalSearchScope scope = filter.getScope();
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
        scope = projectScope.intersectWith(scope);
        for (final PsiClass psiClass : AllClassesSearch.search(scope, manager.getProject())) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (filter.isAccepted(psiClass)) {
                indicator.setText2("Found test class " + psiClass.getQualifiedName());
                set.add(psiClass);
              }
            }
          });
        }
        holder[0] = set.toArray(new PsiClass[set.size()]);
      }
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
    PsiElement[] annotations = PsiTreeUtil.collectElements(element, new PsiElementFilter()
    {
      public boolean isAccepted(PsiElement element) {
        if (!(element instanceof PsiAnnotation)) return false;
        String name = ((PsiAnnotation) element).getQualifiedName();
        if (null == name) return false;
        if (name.startsWith("org.testng.annotations")) {
          return true;
        }
        return false;
      }
    });
    PsiAnnotation[] array = new PsiAnnotation[annotations.length];
    System.arraycopy(annotations, 0, array, 0, annotations.length);
    return array;
  }

  public static boolean isTestNGClass(PsiClass psiClass) {
    return hasTest(psiClass, true, false);
  }

  public static boolean checkTestNGInClasspath(PsiElement psiElement) {
    final Project project = psiElement.getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    if (JavaPsiFacade.getInstance(manager.getProject()).findClass(TestNG.class.getName(), psiElement.getResolveScope()) == null) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        if (Messages.showOkCancelDialog(psiElement.getProject(), "TestNG will be added to module classpath", "Unable to convert.", Messages.getWarningIcon()) !=
            DialogWrapper.OK_EXIT_CODE) {
          return false;
        }
      }
      final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
      if (module == null) return false;
      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      final Library.ModifiableModel libraryModel = model.getModuleLibraryTable().createLibrary().getModifiableModel();
      String url = VfsUtil.getUrlForLibraryRoot(new File(PathUtil.getJarPathForClass(Assert.class)));
      VirtualFile libVirtFile = VirtualFileManager.getInstance().findFileByUrl(url);
      libraryModel.addRoot(libVirtFile, OrderRootType.CLASSES);
      libraryModel.commit();
      model.commit();
    }
    return true;
  }

  public static boolean containsJunitAnnotions(PsiClass psiClass) {
    if (psiClass != null) {
      for (PsiMethod method : psiClass.getMethods()) {
        if (containsJunitAnnotions(method)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean containsJunitAnnotions(PsiMethod method) {
    return method != null && AnnotationUtil.isAnnotated(method, junitAnnotions);
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
    if ("xml".equalsIgnoreCase(virtualFile.getExtension())) {
      final String result = NanoXmlUtil.parseHeader(virtualFile).getRootTagLocalName();
      if (result != null && result.equals(SUITE_TAG_NAME)) {
        return true;
      }
    }
    return false;
  }
}
