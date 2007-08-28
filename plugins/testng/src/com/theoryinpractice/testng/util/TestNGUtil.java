package com.theoryinpractice.testng.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFramework;
import com.intellij.ide.highlighter.XmlFileType;
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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.NanoXmlUtil;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.jetbrains.annotations.NonNls;
import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman Date: Jul 20, 2005 Time: 1:37:36 PM
 */
public class TestNGUtil implements TestFramework
{
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  public static final String TESTNG_GROUP_NAME = "TestNG";

  private static final String TEST_ANNOTATION_FQN = Test.class.getName();
  private static final String[] CONFIG_ANNOTATIONS_FQN = {
      Configuration.class.getName(),
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
    return hasTest(element, checkDisabled, true);
  }

  public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled, boolean checkJavadoc) {
    //LanguageLevel effectiveLanguageLevel = element.getManager().getEffectiveLanguageLevel();
    //boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;
    boolean hasAnnotation = AnnotationUtil.isAnnotated(element, TEST_ANNOTATION_FQN, false);
    if (hasAnnotation) {
      if (checkDisabled) {
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, TEST_ANNOTATION_FQN);
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
        if (AnnotationUtil.isAnnotated(method, TEST_ANNOTATION_FQN, false)) return true;
        if (hasTestJavaDoc(method, checkJavadoc)) return true;
      }
    } else if (element instanceof PsiMethod) {
      //if it's a method, we check if the class it's in has a global @Test annotation
      PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (AnnotationUtil.isAnnotated(psiClass, TEST_ANNOTATION_FQN, false)) {
        //even if it has a global test, we ignore private methods
        boolean isPrivate = element.getModifierList().hasModifierProperty(PsiModifier.PRIVATE);
        return !isPrivate;
      }
      if (hasTestJavaDoc(psiClass, checkJavadoc)) return true;
    }
    return false;
  }

  private static boolean hasTestJavaDoc(PsiDocCommentOwner element, final boolean checkJavadoc) {
    if (checkJavadoc) {
      return getTextJavaDoc(element) != null;
    }
    return false;
  }

  private static PsiDocTag getTextJavaDoc(final PsiDocCommentOwner element) {
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
  public static Map<PsiClass, Collection<PsiMethod>> filterAnnotations(String parameter, Set<String> values, PsiClass[] classes) {
    Map<PsiClass, Collection<PsiMethod>> results = new HashMap<PsiClass, Collection<PsiMethod>>();
    Set<String> test = new HashSet<String>(1);
    test.add(TEST_ANNOTATION_FQN);
    test.addAll(Arrays.asList(CONFIG_ANNOTATIONS_FQN));
    for (PsiClass psiClass : classes) {
      if (isBrokenPsiClass(psiClass)) continue;

      PsiAnnotation annotation;
      try {
        annotation = AnnotationUtil.findAnnotation(psiClass, test);
      } catch (Exception e) {
        LOGGER.error("Exception trying to findAnnotation on " + psiClass.getClass().getName() + ".\n\n" + e.getMessage());
        annotation = null;
      }
      if (annotation != null) {
        PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
        OUTER:
        for (PsiNameValuePair aPair : pair) {
          if (parameter.equals(aPair.getName())) {
            Collection<String> matches = extractValuesFromParameter(aPair);
            //check if any matches are in our values
            for (String s : matches) {
              if (values.contains(s)) {

                results.put(psiClass, new HashSet<PsiMethod>());
                break OUTER;
              }
            }
          }
        }
      } else {
        Collection<String> matches = extractAnnotationValuesFromJavaDoc(getTextJavaDoc(psiClass), parameter);
        for (String s : matches) {
          if (values.contains(s)) {
            results.put(psiClass, new HashSet<PsiMethod>());
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
            PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
            OUTER:
            for (PsiNameValuePair aPair : pair) {
              if (parameter.equals(aPair.getName())) {
                Collection<String> matches = extractValuesFromParameter(aPair);
                for (String s : matches) {
                  if (values.contains(s)) {
                    if (results.get(psiClass) == null)
                      results.put(psiClass, new HashSet<PsiMethod>());
                    results.get(psiClass).add(method);
                    break OUTER;
                  }
                }
              }
            }
          } else {
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

  public static Set<String> getAnnotationValues(String parameter, PsiClass... classes) {
    Set<String> results = new HashSet<String>();
    Set<String> test = new HashSet<String>(1);
    test.add(TEST_ANNOTATION_FQN);
    test.addAll(Arrays.asList(CONFIG_ANNOTATIONS_FQN));
    for (PsiClass psiClass : classes) {
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
    return results;
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
      String groupTag = matcher.group(1);
      String[] groups = groupTag.split("[,\\s]");
      for (String group : groups) {
        results.add(group.trim());
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

  public static PsiClass[] getAllTestClasses(final TestClassFilter filter) {
    final PsiClass holder[][] = new PsiClass[1][];
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable()
    {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

        Collection<PsiClass> set = new HashSet<PsiClass>();
        PsiManager manager = PsiManager.getInstance(filter.getProject());
        PsiSearchHelper helper = manager.getSearchHelper();
        GlobalSearchScope scope = filter.getScope();
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
        scope = projectScope.intersectWith(scope);
        PsiClass apsiclass[] = helper.findAllClasses(scope);
        for (PsiClass psiClass : apsiclass) {
          if (filter.isAccepted(psiClass)) {
            indicator.setText2("Found test class " + psiClass.getQualifiedName());
            set.add(psiClass);
          }
        }
        holder[0] = set.toArray(new PsiClass[set.size()]);
      }
    }, "Searching For Tests...", true, filter.getProject());
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

  public boolean isTestKlass(PsiClass psiClass) {
    return hasTest(psiClass, true, false);
  }

  public PsiMethod findSetUpMethod(final PsiClass psiClass) throws IncorrectOperationException {
    final PsiManager manager = psiClass.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    final PsiMethod patternMethod = factory.createMethodFromText("@org.testng.annotations.BeforeMethod\n protected void setUp() throws Exception {}", null);
    final PsiMethod[] psiMethods = psiClass.getMethods();
    PsiMethod inClass = null;
    for (PsiMethod psiMethod : psiMethods) {
      if (AnnotationUtil.isAnnotated(psiMethod, BeforeMethod.class.getName(), false)) {
        inClass = psiMethod;
        break;
      }
    }
    if (inClass == null) {
      final PsiMethod psiMethod = (PsiMethod)psiClass.add(patternMethod);
      CodeStyleManager.getInstance(psiClass.getProject()).shortenClassReferences(psiClass);
      return psiMethod;
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  public static boolean checkTestNGInClasspath(PsiElement psiElement) {
    final Project project = psiElement.getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    if (manager.findClass(TestNG.class.getName(), psiElement.getResolveScope()) == null) {
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

  public static boolean inheritsITestListener(PsiClass psiClass) {
    for (PsiClass anInterface : psiClass.getInterfaces()) {
      if (anInterface.getQualifiedName().matches("org.testng.(IReporter|ITestListener|internal.annotations.IAnnotationTransformer)"))
        return true;
    }
    return false;
  }

  public static boolean isTestngXML(final VirtualFile virtualFile) {
    if (virtualFile.getName().endsWith(XmlFileType.DEFAULT_EXTENSION)) {
      final NanoXmlUtil.RootTagNameBuilder rootTagNameBuilder = new NanoXmlUtil.RootTagNameBuilder();
      try {
        NanoXmlUtil.parse(virtualFile.getInputStream(), rootTagNameBuilder);
        final String result = rootTagNameBuilder.getResult();
        if (result != null && result.equals(SUITE_TAG_NAME)) {
          return true;
        }
      }
      catch (IOException e) {
        return false;
      }
    }
    return false;
  }
}
