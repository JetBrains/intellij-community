package com.theoryinpractice.testng.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFramework;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.testng.TestNG;
import org.testng.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman Date: Jul 20, 2005 Time: 1:37:36 PM
 */
public class TestNGUtil implements TestFramework
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");

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

    public static PsiClass findPsiClass(String className, Module module, Project project, boolean global) {
        GlobalSearchScope scope;
        if (module != null)
            scope = global ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : GlobalSearchScope.moduleWithDependenciesScope(module);
        else scope = global ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
        return PsiManager.getInstance(project).findClass(className, scope);
    }

    public static PsiPackage getContainingPackage(PsiClass psiclass) {
        return psiclass.getContainingFile().getContainingDirectory().getPackage();
    }

    public static boolean testNGExists(GlobalSearchScope globalsearchscope, Project project) {
        PsiClass found = PsiManager.getInstance(project).findClass(TestNG.class.getName(), globalsearchscope);
        return found != null;
    }

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
        if(qName.equals(TEST_ANNOTATION_FQN)) return true;
        for (String qn : CONFIG_ANNOTATIONS_FQN) {
            if(qName.equals(qn)) return true;
        }
        return false;
    }

    public static boolean hasTest(PsiModifierListOwner element) {
      return hasTest(element, true);
    }
    
    public static boolean hasTest(PsiModifierListOwner element, boolean checkDisabled) {
        //LanguageLevel effectiveLanguageLevel = element.getManager().getEffectiveLanguageLevel();
        //boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;
        boolean hasAnnotation = AnnotationUtil.isAnnotated(element, TEST_ANNOTATION_FQN, false);
        if (hasAnnotation) {
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(element, TEST_ANNOTATION_FQN);
            if (checkDisabled) {
              PsiNameValuePair[] attribs = annotation.getParameterList().getAttributes();
              for (PsiNameValuePair attrib : attribs) {
                  if(attrib.getName().equals("enabled") && attrib.getValue().textMatches("false"))
                      return false;
            }
          }
          return true;
        }
        if (hasTestJavaDoc(element)) return true;
        //now we check all methods for the test annotation
        if (element instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) element;
            for (PsiMethod method : psiClass.getAllMethods()) {
                if (AnnotationUtil.isAnnotated(method, TEST_ANNOTATION_FQN, false)) return true;
                if (hasTestJavaDoc(method)) return true;
            }
        } else if (element instanceof PsiMethod) {
            //if it's a method, we check if the class it's in has a global @Test annotation
            PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (AnnotationUtil.isAnnotated(psiClass, TEST_ANNOTATION_FQN, false)) {
                //even if it has a global test, we ignore private methods
                boolean isPrivate = element.getModifierList().hasModifierProperty(PsiModifier.PRIVATE);
                return !isPrivate;
            }
            if (hasTestJavaDoc(psiClass)) return true;
        }
        return false;
    }

    private static boolean hasTestJavaDoc(PsiElement element) {
        return getTextJavaDoc(element) != null;
    }

    private static PsiDocTag getTextJavaDoc(PsiElement element) {
        for (PsiElement child : element.getChildren()) {
            if (child instanceof PsiDocComment) {
                PsiDocComment doc = (PsiDocComment) child;
                PsiDocTag testTag = doc.findTagByName("testng.test");
                if (testTag != null) return testTag;
            }
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
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiClass, test);
                if (annotation != null) {
                    PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
                    for (PsiNameValuePair aPair : pair) {
                        if (parameter.equals(aPair.getName())) {
                            results.addAll(extractValuesFromParameter(aPair));
                        }
                    }
                } else {
                    results.addAll(extractAnnotationValuesFromJavaDoc(getTextJavaDoc(psiClass), parameter));
                }

                PsiMethod[] methods = psiClass.getMethods();
                for (PsiMethod method : methods) {
                    if (method != null) {
                        annotation = AnnotationUtil.findAnnotation(method, test);
                        if (annotation != null) {
                            PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
                            for (PsiNameValuePair aPair : pair) {
                                if (parameter.equals(aPair.getName())) {
                                    results.addAll(extractValuesFromParameter(aPair));
                                }
                            }
                        } else {
                            results.addAll(extractAnnotationValuesFromJavaDoc(getTextJavaDoc(method), parameter));
                        }
                    }
                }
            }
        }
        return results;
    }

    private static Collection<String> extractAnnotationValuesFromJavaDoc(PsiDocTag tag, String parameter) {
        if (tag == null) return Collections.emptyList();
        Collection<String> results = new ArrayList<String>();
        Matcher matcher = Pattern.compile("\\@testng.test(?:.*)" + parameter + "\\s*=\\s*\"(.*)\".*").matcher(tag.getText());
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
    return hasTest(psiClass);
  }
}
