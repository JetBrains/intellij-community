package com.intellij.execution.junit;

import com.intellij.execution.*;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;

import java.util.*;

public class JUnitUtil {
  private static final String TESTCASE_CLASS = "junit.framework.TestCase";
  private static final String TEST_INTERFACE = "junit.framework.Test";
  private static final String TESTSUITE_CLASS = "junit.framework.TestSuite";
  private static final String SUITE_METHOD = "suite";

  public static boolean isSuiteMethod(final PsiMethod psiMethod) {
    if (psiMethod == null) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (psiMethod.isConstructor()) return false;
    final PsiType returnType = psiMethod.getReturnType();
    if (!returnType.equalsToText(TEST_INTERFACE) && !returnType.equalsToText(TESTSUITE_CLASS)) return false;
    final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
    return parameters.length == 0;
  }

  public static interface FindCallback {
    /**
     * Invoked in dispatch thread
     */
    void found(PsiClass[] classes);
  }

  public static void findTestsWithProgress(final FindCallback callback, final TestClassFilter classFilter) {
    if (isSyncSearch()) {
      callback.found(ConfigurationUtil.getAllTestClasses(classFilter));
      return;
    }

    final PsiClass[][] result = new PsiClass[1][];
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(
      new Runnable() {
        public void run() {
          result[0] = ConfigurationUtil.getAllTestClasses(classFilter);
        }
      },
      "Searching For Tests...",
      true,
      classFilter.getProject()
    );

    if (result[0] != null) {
      callback.found(result[0]);
    }
  }

  private static boolean isSyncSearch() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public static boolean isTestMethod(final Location<? extends PsiMethod> location) {
    final Location<PsiClass> aClass = location.getParent(PsiClass.class);
    if (aClass == null) return false;
    if (!isTestCaseClass(aClass)) return false;
    final PsiMethod psiMethod = location.getPsiElement();
    if (psiMethod.isConstructor()) return false;
    if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    if (psiMethod.getParameterList().getParameters().length > 0) return false;
    if (psiMethod.hasModifierProperty(PsiModifier.STATIC) && SUITE_METHOD.equals(psiMethod.getName())) return false;
    final PsiClass testCaseClass;
    try {
      testCaseClass = getTestCaseClass(location);
    } catch (NoJUnitException e) {
      return false;
    }
    if (!psiMethod.getContainingClass().isInheritor(testCaseClass, true)) return false;
    return true;
  }

  private static boolean isTestCaseInheritor(final PsiClass aClass) {
    final PsiClass testCaseClass;
    try {
      testCaseClass = getTestCaseClass(PsiLocation.fromPsiElement(aClass));
    }
    catch (NoJUnitException e) {
      return false;
    }
    return aClass.isInheritor(testCaseClass, true);
  }

  /**
   *
   * @param aClassLocation
   * @return true iff aClassLocation can be used as JUnit test class.
   */
  public static boolean isTestCaseClass(final Location<PsiClass> aClassLocation) {
    return isTestCaseClass(aClassLocation.getPsiElement());
  }

  public static boolean isTestCaseClass(final PsiClass psiClass) {
    if (!ExecutionUtil.isRunnableClass(psiClass)) return false;
    if (isTestCaseInheritor(psiClass)) return true;

    final PsiMethod[] methods = psiClass.findMethodsByName(SUITE_METHOD, false);
    for (int i = 0; i < methods.length; i++) {
      final PsiMethod method = methods[i];
      if (isSuiteMethod(method)) return true;
    }
    return false;
  }

  private static PsiClass getTestCaseClass(final Location location) throws NoJUnitException {
    final Location<PsiClass> ancestorOrSelf = location.getAncestorOrSelf(PsiClass.class);
    final PsiClass aClass = ancestorOrSelf.getPsiElement();
    return getTestCaseClass(ExecutionUtil.findModule(aClass));
  }

  public static PsiClass getTestCaseClass(final Module module) throws NoJUnitException {
    if (module == null) throw new NoJUnitException();
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    return getTestCaseClass(scope, module.getProject());
  }

  public static PsiClass getTestCaseClass(final SourceScope scope) throws NoJUnitException {
    if (scope == null) throw new NoJUnitException();
    return getTestCaseClass(scope.getLibrariesScope(), scope.getProject());
  }

  private static PsiClass getTestCaseClass(final GlobalSearchScope scope, final Project project) throws NoJUnitException {
    final PsiClass testCaseClass = PsiManager.getInstance(project).findClass(TESTCASE_CLASS, scope); // TODO do not search in sources;
    if (testCaseClass == null) throw new NoJUnitException();
    return testCaseClass;
  }

  public static class  TestMethodFilter implements Condition<PsiMethod> {
    private final PsiClass myClass;

    public TestMethodFilter(final PsiClass aClass) {
      myClass = aClass;
    }

    public boolean value(final PsiMethod method) {
      return isTestMethod(MethodLocation.elementInClass(method, myClass));
    }
  }

  public static PsiClass findPsiClass(final String qualifiedName, final Module module, final Project project, final boolean searchInLibs) {
    final GlobalSearchScope scope;
    if (module != null)
      scope = searchInLibs
              ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
              : GlobalSearchScope.moduleWithDependenciesScope(module);
    else scope = searchInLibs ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    return PsiManager.getInstance(project).findClass(qualifiedName, scope);
  }

  public static PsiPackage getContainingPackage(final PsiClass psiClass) {
    return psiClass.getContainingFile().getContainingDirectory().getPackage();
  }

  public static PsiClass getTestClass(final PsiElement element) {
    return JUnitConfigurationProducer.getTestClass(PsiLocation.fromPsiElement(element));
  }

  public static PsiMethod getTestMethod(final PsiElement element) {
    final PsiManager manager = element.getManager();
    final Location<PsiElement> location = PsiLocation.fromPsiElement(manager.getProject(), element);
    for (Iterator<Location<? extends PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final Location<? extends PsiMethod> methodLocation = iterator.next();
      if (isTestMethod(methodLocation)) return methodLocation.getPsiElement();
    }
    return null;
  }

  /**
   * @param collection
   * @param comparator returns 0 iff elemets are incomparable.
   * @return maximum elements
   */
  public static <T> Collection<T> findMaximums(final Collection<T> collection, final Comparator<T> comparator) {
    final ArrayList<T> maximums = new ArrayList<T>();
    loop:
    for (Iterator<T> iterator = collection.iterator(); iterator.hasNext();) {
      final T candidate = iterator.next();
      for (Iterator<T> iterator1 = collection.iterator(); iterator1.hasNext();) {
        final T element = iterator1.next();
        if (comparator.compare(element, candidate) > 0) continue loop;
      }
      maximums.add(candidate);
    }
    return maximums;
  }

  public static Map<Module, Collection<Module>> buildAllDependencies(final Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    Graph<Module> graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<Module>() {
          public Collection<Module> getNodes() {
            return Arrays.asList(modules);
          }

          public Iterator<Module> getIn(Module module) {
            return Arrays.asList(ModuleRootManager.getInstance(module).getDependencies()).iterator();
          }
        }));

    Map<Module, Collection<Module>> result = new HashMap<Module, Collection<Module>>();
    for (Iterator<Module> iterator = graph.getNodes().iterator(); iterator.hasNext();) {
      final Module module = iterator.next();
      buildDependenciesForModule(module, graph, result);
    }
    return result;
  }

  private static void buildDependenciesForModule(final Module module, final Graph<Module> graph, Map<Module, Collection<Module>> map) {
    final Set<Module> deps = new HashSet<Module>();
    map.put(module, deps);

    new Object() {
      void traverse(Module m) {
        for (Iterator<Module> iterator = graph.getIn(m); iterator.hasNext();) {
          final Module dep = iterator.next();
          if (!deps.contains(dep)) {
            deps.add(dep);
            traverse(dep);
          }
        }
      }
    }.traverse(module);

    graph.getIn(module);
  }

  /*public static Map<Module, Collection<Module>> buildAllDependencies(final Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getSortedModules();
    final HashMap<Module, Collection<Module>> lessers = new HashMap<Module, Collection<Module>>();
    int prevProcessedCount = 0;
    while (modules.length > lessers.size()) {
      for (int i = 0; i < modules.length; i++) {
        final Module module = modules[i];
        if (lessers.containsKey(module)) continue;
        final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
        if (lessers.keySet().containsAll(Arrays.asList(dependencies))) {
          final HashSet<Module> allDependencies = new HashSet<Module>();
          for (int j = 0; j < dependencies.length; j++) {
            final Module dependency = dependencies[j];
            allDependencies.add(dependency);
            allDependencies.addAll(lessers.get(dependency));
          }
          lessers.put(module, allDependencies);
        }
      }
      if (lessers.size() == prevProcessedCount) return null;
      prevProcessedCount = lessers.size();
    }
    return lessers;
  }*/

  public static class ModuleOfClass implements Convertor<PsiClass, Module> {
    private final ProjectFileIndex myFileIndex;

    public ModuleOfClass(final ProjectFileIndex fileIndex) {
      myFileIndex = fileIndex;
    }

    public ModuleOfClass(final Project project) {
      this(ProjectRootManager.getInstance(project).getFileIndex());
    }

    public Module convert(final PsiClass psiClass) {
      if (psiClass == null || !psiClass.isValid()) return null;
      return ModuleUtil.findModuleForPsiElement(psiClass);
    }
  }

  public static class NoJUnitException extends CantRunException {
    public NoJUnitException() {
      super("No junit.jar");
    }
  }
}
