// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.junit.internal.MethodSorter;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class JUnit3RunnerWithInners extends Runner implements Filterable, Sortable {
    private static final Set<Class<?>> requestedRunners = new HashSet<>();

    private JUnit38ClassRunner delegateRunner;
    private final Class<?> testClass;
    private boolean isFakeTest = false;

    public JUnit3RunnerWithInners(Class<?> testClass) {
        this.testClass = testClass;
        requestedRunners.add(testClass);
    }

    @Override
    public void run(RunNotifier notifier) {
        initialize();
        delegateRunner.run(notifier);
    }

    @Override
    public Description getDescription() {
        initialize();
        return isFakeTest ? Description.EMPTY : delegateRunner.getDescription();
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        initialize();
        delegateRunner.filter(filter);
    }

    @Override
    public void sort(Sorter sorter) {
        initialize();
        delegateRunner.sort(sorter);
    }

    protected void initialize() {
        if (delegateRunner != null) return;
        delegateRunner = new JUnit38ClassRunner(getCollectedTests());
    }

    private Test getCollectedTests() {
        List<Class<?>> innerClasses = collectDeclaredClasses(testClass, false);
        Set<Class<?>> unprocessedInnerClasses = unprocessedClasses(innerClasses);

        if (unprocessedInnerClasses.isEmpty()) {
            if (!innerClasses.isEmpty() && !hasTestMethods(testClass)) {
                isFakeTest = true;
                return new JUnit3RunnerWithInners.FakeEmptyClassTest(testClass);
            } else {
                return new TestSuite(testClass.asSubclass(TestCase.class));
            }
        } else if (unprocessedInnerClasses.size() == innerClasses.size()) {
            return createTreeTestSuite(testClass);
        } else {
            return new TestSuite(testClass.asSubclass(TestCase.class));
        }
    }

    private static Test createTreeTestSuite(Class<?> root) {
        Set<Class<?>> classes = new LinkedHashSet<>(collectDeclaredClasses(root, true));
        Map<Class<?>, TestSuite> classSuites = new HashMap<>();

        for (Class<?> aClass : classes) {
            classSuites.put(aClass, hasTestMethods(aClass) ? new TestSuite(aClass) : new TestSuite(aClass.getCanonicalName()));
        }

        for (Class<?> aClass : classes) {
            if (aClass.getEnclosingClass() != null && classes.contains(aClass.getEnclosingClass())) {
                classSuites.get(aClass.getEnclosingClass()).addTest(classSuites.get(aClass));
            }
        }

        return classSuites.get(root);
    }

    private static Set<Class<?>> unprocessedClasses(Collection<Class<?>> classes) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> aClass : classes) {
            if (!requestedRunners.contains(aClass)) {
                result.add(aClass);
            }
        }

        return result;
    }

    private static List<Class<?>> collectDeclaredClasses(Class<?> klass, boolean withItself) {
        List<Class<?>> result = new ArrayList<>();
        if (withItself) {
            result.add(klass);
        }

        for (Class<?> aClass : klass.getDeclaredClasses()) {
            result.addAll(collectDeclaredClasses(aClass, true));
        }

        return result;
    }

    private static boolean hasTestMethods(Class<?> klass) {
        for (Class<?> currentClass = klass; Test.class.isAssignableFrom(currentClass); currentClass = currentClass.getSuperclass()) {
            for (Method each : MethodSorter.getDeclaredMethods(currentClass)) {
                if (isTestMethod(each)) return true;
            }
        }

        return false;
    }

    static class FakeEmptyClassTest implements Test, Filterable {
        private final String className;

        FakeEmptyClassTest(Class<?> klass) {
            this.className = klass.getName();
        }

        @Override
        public int countTestCases() {
            return 0;
        }

        @Override
        public void run(TestResult result) {
            result.startTest(this);
            result.endTest(this);
        }

        @Override
        public String toString() {
            return "Empty class with inners for " + className;
        }

        @Override
        public void filter(Filter filter) throws NoTestsRemainException {
            throw new NoTestsRemainException();
        }
    }

    static boolean isTestMethod(Method method) {
        return method.getParameterTypes().length == 0 &&
               method.getName().startsWith("test") &&
               method.getReturnType().equals(Void.TYPE) &&
               Modifier.isPublic(method.getModifiers());
    }
}