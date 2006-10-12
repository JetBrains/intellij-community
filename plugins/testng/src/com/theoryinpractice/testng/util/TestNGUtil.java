package com.theoryinpractice.testng.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.model.TestClassFilter;
import org.testng.TestNG;
import org.testng.annotations.*;

/**
 * @author Hani Suleiman Date: Jul 20, 2005 Time: 1:37:36 PM
 */
public class TestNGUtil
{
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
            "testng.beforeClass",
            "testng.beforeGroups",
            "testng.beforeMethod",
            "testng.beforeSuite",
            "testng.beforeTest",
            "testng.afterClass",
            "testng.afterGroups",
            "testng.afterMethod",
            "testng.afterSuite",
            "testng.afterTest"
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

    public static boolean hasTest(PsiModifierListOwner element) {
        //LanguageLevel effectiveLanguageLevel = element.getManager().getEffectiveLanguageLevel();
        //boolean is15 = effectiveLanguageLevel != LanguageLevel.JDK_1_4 && effectiveLanguageLevel != LanguageLevel.JDK_1_3;
        if (AnnotationUtil.isAnnotated(element, TEST_ANNOTATION_FQN, false)) return true;
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
            if (AnnotationUtil.isAnnotated(psiClass, TEST_ANNOTATION_FQN, false)) return true;
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

    public static String[] getAllGroups(PsiClass[] classes) {
        Collection<String> results = new HashSet<String>();
        Set<String> test = new HashSet<String>(1);
        test.add(TEST_ANNOTATION_FQN);
        for (PsiClass psiClass : classes) {
            if (hasTest(psiClass)) {
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(psiClass, test);
                if (annotation != null) {
                    PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
                    for (PsiNameValuePair aPair : pair) {
                        if ("groups".equals(aPair.getName())) {
                            extractGroupsFromParameter(aPair, results);
                        }
                    }
                } else {
                    extractGroupsFromJavaDoc(getTextJavaDoc(psiClass), results);
                }

                PsiMethod[] methods = psiClass.getMethods();
                for (PsiMethod method : methods) {
                    annotation = AnnotationUtil.findAnnotation(method, test);
                    if (annotation != null) {
                        PsiNameValuePair[] pair = annotation.getParameterList().getAttributes();
                        for (PsiNameValuePair aPair : pair) {
                            if ("groups".equals(aPair.getName())) {
                                extractGroupsFromParameter(aPair, results);
                            }
                        }
                    } else {
                        extractGroupsFromJavaDoc(getTextJavaDoc(method), results);
                    }
                }
            }
        }
        String[] array = results.toArray(new String[results.size()]);
        Arrays.sort(array);
        return array;
    }

    private static void extractGroupsFromJavaDoc(PsiDocTag tag, Collection<String> results) {
        if (tag == null) return;

        Matcher matcher = Pattern.compile("\\@testng.test(?:.*)groups\\s*=\\s*\"(.*)\".*").matcher(tag.getText());
        if (matcher.matches()) {
            String groupTag = matcher.group(1);
            String[] groups = groupTag.split("[,\\s]");
            for (String group : groups) {
                results.add(group.trim());
            }
        }
    }

    private static void extractGroupsFromParameter(PsiNameValuePair aPair, Collection<String> results) {
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
}
