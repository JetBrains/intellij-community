// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test.testFramework;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.DocumentCommitProcessor;
import com.intellij.psi.impl.DocumentCommitThread;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.*;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import kotlin.Unit;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.idea.testFramework.MockComponentManagerCreationTracer;
import org.jetbrains.kotlin.types.AbstractTypeChecker;
import org.junit.Assert;
import org.junit.runner.Description;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.intellij.testFramework.EdtTestUtilKt.dispatchAllEventsInIdeEventQueue;
import static com.intellij.testFramework.common.Cleanup.cleanupSwingDataStructures;

@SuppressWarnings("ALL")
public abstract class KtUsefulTestCase extends TestCase {
    public static final String TEMP_DIR_MARKER = "unitTest_";
    public static final boolean OVERWRITE_TESTDATA = Boolean.getBoolean("idea.tests.overwrite.data");

    private static final String ORIGINAL_TEMP_DIR = FileUtil.getTempDirectory();

    private static final Map<String, Long> TOTAL_SETUP_COST_MILLIS = new HashMap<>();
    private static final Map<String, Long> TOTAL_TEARDOWN_COST_MILLIS = new HashMap<>();

    private Application application;

    static {
        Logger.setFactory(TestLoggerFactory.class);
    }

    protected static final Logger LOG = Logger.getInstance(KtUsefulTestCase.class);

    @NotNull
    private final Disposable myTestRootDisposable = new TestDisposable();

    static Path ourPathToKeep;
    private final List<String> myPathsToKeep = new ArrayList<>();

    private String myTempDir;

    private static final String DEFAULT_SETTINGS_EXTERNALIZED;
    private static final CodeInsightSettings defaultSettings = new CodeInsightSettings();
    static {
        // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
        System.setProperty("apple.awt.UIElement", "true");

        try {
            Element oldS = new Element("temp");
            defaultSettings.writeExternal(oldS);
            DEFAULT_SETTINGS_EXTERNALIZED = JDOMUtil.writeElement(oldS);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        // -- KOTLIN ADDITIONAL START --

        try {
            Class<?> flexTypeClass = Class.forName("org.jetbrains.kotlin.types.FlexibleTypeImpl");
            Field assertionsField = flexTypeClass.getDeclaredField("RUN_SLOW_ASSERTIONS");
            assertionsField.setAccessible(true);
            assertionsField.set(null, true);
        } catch (Throwable ignore) {
            // ignore
        }
        AbstractTypeChecker.RUN_SLOW_ASSERTIONS = true;

        // -- KOTLIN ADDITIONAL END --
    }

    /**
     * Pass here the exception you want to be thrown first
     * E.g.<pre>
     * {@code
     *   void tearDown() {
     *     try {
     *       doTearDowns();
     *     }
     *     catch(Exception e) {
     *       addSuppressedException(e);
     *     }
     *     finally {
     *       super.tearDown();
     *     }
     *   }
     * }
     * </pre>
     *
     */
    protected void addSuppressedException(@NotNull Throwable e) {
        List<Throwable> list = mySuppressedExceptions;
        if (list == null) {
            mySuppressedExceptions = list = new SmartList<>();
        }
        list.add(e);
    }
    private List<Throwable> mySuppressedExceptions;


    public KtUsefulTestCase() {
    }

    public KtUsefulTestCase(@NotNull String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        // -- KOTLIN ADDITIONAL START --
        application = ApplicationManager.getApplication();

        if (application != null && application.isDisposed()) {
            MockComponentManagerCreationTracer.diagnoseDisposedButNotClearedApplication(application);
        }
        // -- KOTLIN ADDITIONAL END --

        super.setUp();

        boolean isStressTest = isStressTest();
        ApplicationManagerEx.setInStressTest(isStressTest);
        if (isPerformanceTest()) {
            Timings.getStatistics();
        }

        // turn off Disposer debugging for performance tests
        Disposer.setDebugMode(!isStressTest);

        if (isIconRequired()) {
            // ensure that IconLoader will use fake empty icon
            IconLoader.deactivate();
            //IconManager.activate();
        }
    }

    protected boolean isIconRequired() {
        return false;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            // don't use method references here to make stack trace reading easier
            //noinspection Convert2MethodRef
            new RunAll(
                    () -> {
                        if (isIconRequired()) {
                            //IconManager.deactivate();
                        }
                    },
                    () -> disposeRootDisposable(),
                    () -> cleanupSwingDataStructures(),
                    () -> cleanupDeleteOnExitHookList(),
                    () -> Disposer.setDebugMode(true),
                    () -> waitForAppLeakingThreads(10, TimeUnit.SECONDS)
            ).run(ObjectUtils.notNull(mySuppressedExceptions, Collections.emptyList()));
        }
        finally {
            // -- KOTLIN ADDITIONAL START --
            TestApplicationUtilKt.resetApplicationToNull(application);
            application = null;
            // -- KOTLIN ADDITIONAL END --
        }
    }

    protected final void disposeRootDisposable() {
        Disposer.dispose(getTestRootDisposable());
    }

    protected void addTmpFileToKeep(@NotNull File file) {
        myPathsToKeep.add(file.getPath());
    }

    private boolean shouldKeepTmpFile(@NotNull File file) {
        String path = file.getPath();
        if (FileUtil.pathsEqual(path, ourPathToKeep.toString())) return true;
        for (String pathToKeep : myPathsToKeep) {
            if (FileUtil.pathsEqual(path, pathToKeep)) return true;
        }
        return false;
    }

    private static final Set<String> DELETE_ON_EXIT_HOOK_DOT_FILES;
    private static final Class<?> DELETE_ON_EXIT_HOOK_CLASS;
    static {
        Class<?> aClass;
        try {
            aClass = Class.forName("java.io.DeleteOnExitHook");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        @SuppressWarnings("unchecked") Set<String> files = ReflectionUtil.getStaticFieldValue(aClass, Set.class, "files");
        DELETE_ON_EXIT_HOOK_CLASS = aClass;
        DELETE_ON_EXIT_HOOK_DOT_FILES = files;
    }

    @SuppressWarnings("SynchronizeOnThis")
    private static void cleanupDeleteOnExitHookList() {
        // try to reduce file set retained by java.io.DeleteOnExitHook
        List<String> list;
        synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
            if (DELETE_ON_EXIT_HOOK_DOT_FILES.isEmpty()) return;
            list = new ArrayList<>(DELETE_ON_EXIT_HOOK_DOT_FILES);
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            String path = list.get(i);
            File file = new File(path);
            if (file.delete() || !file.exists()) {
                synchronized (DELETE_ON_EXIT_HOOK_CLASS) {
                    DELETE_ON_EXIT_HOOK_DOT_FILES.remove(path);
                }
            }
        }
    }

    static void doCheckForSettingsDamage(@NotNull CodeStyleSettings oldCodeStyleSettings, @NotNull CodeStyleSettings currentCodeStyleSettings) {
        final CodeInsightSettings settings = CodeInsightSettings.getInstance();
        // don't use method references here to make stack trace reading easier
        //noinspection Convert2MethodRef
        RunAll.runAll(
                () -> {
                    try {
                        checkCodeInsightSettingsEqual(defaultSettings, settings);
                    }
                    catch (AssertionError error) {
                        CodeInsightSettings clean = new CodeInsightSettings();
                        for (Field field : clean.getClass().getFields()) {
                            try {
                                ReflectionUtil.copyFieldValue(clean, settings, field);
                            }
                            catch (Exception ignored) {
                            }
                        }
                        throw error;
                    }
                },
                () -> {
                    currentCodeStyleSettings.getIndentOptions(JavaFileType.INSTANCE);
                    try {
                        checkCodeStyleSettingsEqual(oldCodeStyleSettings, currentCodeStyleSettings);
                    }
                    finally {
                        currentCodeStyleSettings.clearCodeStyleSettings();
                    }
                }
        );
    }

    @NotNull
    public Disposable getTestRootDisposable() {
        return myTestRootDisposable;
    }

    @Override
    protected void runTest() throws Throwable {
        final Throwable[] throwables = new Throwable[1];
        var testDescription = Description.createTestDescription(getClass(), getName());
        Runnable runnable = () -> {
            try {
                TestLoggerFactory.onTestStarted();
                super.runTest();
                TestLoggerFactory.onTestFinished(true, testDescription);
            } catch (InvocationTargetException e) {
                TestLoggerFactory.logTestFailure(e);
                TestLoggerFactory.onTestFinished(false, testDescription);
                e.fillInStackTrace();
                throwables[0] = e.getTargetException();
            }
            catch (IllegalAccessException e) {
                TestLoggerFactory.logTestFailure(e);
                TestLoggerFactory.onTestFinished(false, testDescription);
                e.fillInStackTrace();
                throwables[0] = e;
            }
            catch (Throwable e) {
                TestLoggerFactory.logTestFailure(e);
                TestLoggerFactory.onTestFinished(false, testDescription);
                throwables[0] = e;
            }
        };

        invokeTestRunnable(runnable);

        if (throwables[0] != null) {
            throw throwables[0];
        }
    }

    protected boolean shouldRunTest() {
        return TestFrameworkUtil.canRunTest(getClass());
    }

    protected void invokeTestRunnable(@NotNull Runnable runnable) throws Exception {
        if (runInDispatchThread()) {
            EdtTestUtilKt.runInEdtAndWait(() -> {
                runnable.run();
                return null;
            });
        }
        else {
            runnable.run();
        }
    }

    protected void defaultRunBare() throws Throwable {
        Throwable exception = null;
        try {
            long setupStart = System.nanoTime();
            setUp();
            long setupCost = (System.nanoTime() - setupStart) / 1000000;
            logPerClassCost(setupCost, TOTAL_SETUP_COST_MILLIS);

            runTest();
        }
        catch (Throwable running) {
            exception = running;
        }
        finally {
            try {
                long teardownStart = System.nanoTime();
                tearDown();
                long teardownCost = (System.nanoTime() - teardownStart) / 1000000;
                logPerClassCost(teardownCost, TOTAL_TEARDOWN_COST_MILLIS);
            }
            catch (Throwable tearingDown) {
                if (exception == null) {
                    exception = tearingDown;
                }
                else {
                    exception = new CompoundRuntimeException(Arrays.asList(exception, tearingDown));
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Logs the setup cost grouped by test fixture class (superclass of the current test class).
     *
     * @param cost setup cost in milliseconds
     */
    private void logPerClassCost(long cost, @NotNull Map<String, Long> costMap) {
        Class<?> superclass = getClass().getSuperclass();
        Long oldCost = costMap.get(superclass.getName());
        long newCost = oldCost == null ? cost : oldCost + cost;
        costMap.put(superclass.getName(), newCost);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void logSetupTeardownCosts() {
        System.out.println("Setup costs");
        long totalSetup = 0;
        for (Map.Entry<String, Long> entry : TOTAL_SETUP_COST_MILLIS.entrySet()) {
            System.out.println(String.format("  %s: %d ms", entry.getKey(), entry.getValue()));
            totalSetup += entry.getValue();
        }
        System.out.println("Teardown costs");
        long totalTeardown = 0;
        for (Map.Entry<String, Long> entry : TOTAL_TEARDOWN_COST_MILLIS.entrySet()) {
            System.out.println(String.format("  %s: %d ms", entry.getKey(), entry.getValue()));
            totalTeardown += entry.getValue();
        }
        System.out.println(String.format("Total overhead: setup %d ms, teardown %d ms", totalSetup, totalTeardown));
        System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.totalSetupMs' value='%d']", totalSetup));
        System.out.println(String.format("##teamcity[buildStatisticValue key='ideaTests.totalTeardownMs' value='%d']", totalTeardown));
    }

    @Override
    public void runBare() throws Throwable {
        if (!shouldRunTest()) return;

        if (runInDispatchThread()) {
            UITestUtil.replaceIdeEventQueueSafely();
            EdtTestUtil.runInEdtAndWait(this::defaultRunBare);
        }
        else {
            defaultRunBare();
        }
    }

    protected boolean runInDispatchThread() {
        IdeaTestExecutionPolicy policy = IdeaTestExecutionPolicy.current();
        if (policy != null) {
            return policy.runInDispatchThread();
        }
        return true;
    }

    /**
     * If you want a more shorter name than runInEdtAndWait.
     */
    protected void edt(@NotNull ThrowableRunnable<Throwable> runnable) {
        try {
            EdtTestUtil.runInEdtAndWait(runnable);
        } catch (Throwable throwable) {
            LOG.warn(throwable);
        }
    }

    @NotNull
    public static String toString(@NotNull Iterable<?> collection) {
        if (!collection.iterator().hasNext()) {
            return "<empty>";
        }

        final StringBuilder builder = new StringBuilder();
        for (final Object o : collection) {
            if (o instanceof Set) {
                builder.append(new TreeSet<>((Set<?>)o));
            }
            else {
                builder.append(o);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    @NotNull
    public static String toString(@NotNull Object[] collection, @NotNull String separator) {
        return toString(Arrays.asList(collection), separator);
    }

    @NotNull
    public static String toString(@NotNull Collection<?> collection, @NotNull String separator) {
        List<String> list = ContainerUtil.sorted(ContainerUtil.map(collection, String::valueOf));
        StringBuilder builder = new StringBuilder();
        boolean flag = false;
        for (final String o : list) {
            if (flag) {
                builder.append(separator);
            }
            builder.append(o);
            flag = true;
        }
        return builder.toString();
    }

    public static void printThreadDump() {
        PerformanceWatcher.dumpThreadsToConsole("Thread dump:");
    }

    @NotNull
    protected <T extends Disposable> T disposeOnTearDown(@NotNull T disposable) {
        Disposer.register(getTestRootDisposable(), disposable);
        return disposable;
    }

    @NotNull
    protected String getTestName(boolean lowercaseFirstLetter) {
        return getTestName(getName(), lowercaseFirstLetter);
    }

    @NotNull
    public static String getTestName(@Nullable String name, boolean lowercaseFirstLetter) {
        return name == null ? "" : PlatformTestUtil.getTestName(name, lowercaseFirstLetter);
    }

    @NotNull
    protected String getTestDirectoryName() {
        final String testName = getTestName(true);
        return testName.replaceAll("_.*", "");
    }

    protected static void clearFields(@NotNull Object test) throws IllegalAccessException {
        Class<?> aClass = test.getClass();
        while (aClass != null) {
            clearDeclaredFields(test, aClass);
            aClass = aClass.getSuperclass();
        }
    }

    public static void clearDeclaredFields(@NotNull Object test, @NotNull Class<?> aClass) throws IllegalAccessException {
        for (final Field field : aClass.getDeclaredFields()) {
            final String name = field.getDeclaringClass().getName();
            if (!name.startsWith("junit.framework.") && !name.startsWith("com.intellij.testFramework.")) {
                final int modifiers = field.getModifiers();
                if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    field.set(test, null);
                }
            }
        }
    }

    private static void checkCodeStyleSettingsEqual(@NotNull CodeStyleSettings expected, @NotNull CodeStyleSettings settings) {
        if (!expected.equals(settings)) {
            Element oldS = new Element("temp");
            expected.writeExternal(oldS);
            Element newS = new Element("temp");
            settings.writeExternal(newS);

            String newString = JDOMUtil.writeElement(newS);
            String oldString = JDOMUtil.writeElement(oldS);
            Assert.assertEquals("Code style settings damaged", oldString, newString);
        }
    }

    private static void checkCodeInsightSettingsEqual(@NotNull CodeInsightSettings oldSettings, @NotNull CodeInsightSettings settings) {
        if (!oldSettings.equals(settings)) {
            Element newS = new Element("temp");
            settings.writeExternal(newS);
            Assert.assertEquals("Code insight settings damaged", DEFAULT_SETTINGS_EXTERNALIZED, JDOMUtil.writeElement(newS));
        }
    }

    public boolean isPerformanceTest() {
        String testName = getName();
        String className = getClass().getSimpleName();
        return TestFrameworkUtil.isPerformanceTest(testName, className, getClass());
    }

    /**
     * @return true for a test which performs A LOT of computations.
     * Such test should typically avoid performing expensive checks, e.g. data structure consistency complex validations.
     * If you want your test to be treated as "Stress", please mention one of these words in its name: "Stress", "Slow".
     * For example: {@code public void testStressPSIFromDifferentThreads()}
     */
    public boolean isStressTest() {
        return isStressTest(getName(), getClass().getName(), getClass());
    }

    private static boolean isStressTest(String testName, String className, Class<?> aClass) {
        return TestFrameworkUtil.isPerformanceTest(testName, className, aClass) ||
               containsStressWords(testName) ||
               containsStressWords(className);
    }

    private static boolean containsStressWords(@Nullable String name) {
        return name != null && (name.contains("Stress") || name.contains("Slow"));
    }

    public static void doPostponedFormatting(@NotNull Project project) {
        DocumentUtil.writeInRunUndoTransparentAction(() -> {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
        });
    }

    /**
     * Checks that code block throw corresponding exception.
     *
     * @param exceptionCase Block annotated with some exception type
     */
    protected void assertException(@NotNull AbstractExceptionCase<?> exceptionCase) {
        assertException(exceptionCase, null);
    }

    /**
     * Checks that code block throw corresponding exception with expected error msg.
     * If expected error message is null it will not be checked.
     *
     * @param exceptionCase    Block annotated with some exception type
     * @param expectedErrorMsg expected error message
     */
    @SuppressWarnings("unchecked")
    protected void assertException(@NotNull AbstractExceptionCase exceptionCase, @Nullable String expectedErrorMsg) {
        assertExceptionOccurred(true, exceptionCase, expectedErrorMsg);
    }

    /**
     * Checks that the code block throws an exception of the specified class.
     *
     * @param exceptionClass   Expected exception type
     * @param runnable         Block annotated with some exception type
     */
    public static <T extends Throwable> void assertThrows(@NotNull Class<? extends Throwable> exceptionClass,
            @NotNull ThrowableRunnable<T> runnable) {
        assertThrows(exceptionClass, null, runnable);
    }

    /**
     * Checks that the code block throws an exception of the specified class with expected error msg.
     * If expected error message is null it will not be checked.
     *
     * @param exceptionClass   Expected exception type
     * @param expectedErrorMsgPart expected error message, of any
     * @param runnable         Block annotated with some exception type
     */
    @SuppressWarnings({"unchecked", "SameParameterValue"})
    public static <T extends Throwable> void assertThrows(@NotNull Class<? extends Throwable> exceptionClass,
            @Nullable String expectedErrorMsgPart,
            @NotNull ThrowableRunnable<T> runnable) {
        assertExceptionOccurred(true, new AbstractExceptionCase() {
            @Override
            public Class<Throwable> getExpectedExceptionClass() {
                return (Class<Throwable>)exceptionClass;
            }

            @Override
            public void tryClosure() throws Throwable {
                runnable.run();
            }
        }, expectedErrorMsgPart);
    }

    /**
     * Checks that code block doesn't throw corresponding exception.
     *
     * @param exceptionCase Block annotated with some exception type
     */
    protected <T extends Throwable> void assertNoException(@NotNull AbstractExceptionCase<T> exceptionCase) throws T {
        assertExceptionOccurred(false, exceptionCase, null);
    }

    protected void assertNoThrowable(@NotNull Runnable closure) {
        String throwableName = null;
        try {
            closure.run();
        }
        catch (Throwable thr) {
            throwableName = thr.getClass().getName();
        }
        assertNull(throwableName);
    }

    private static <T extends Throwable> void assertExceptionOccurred(boolean shouldOccur,
            @NotNull AbstractExceptionCase<T> exceptionCase,
            String expectedErrorMsgPart) throws T {
        boolean wasThrown = false;
        try {
            exceptionCase.tryClosure();
        }
        catch (Throwable e) {
            Throwable cause = e;

            if (shouldOccur) {
                wasThrown = true;
                KotlinTestUtils.assertInstanceOf(cause, exceptionCase.getExpectedExceptionClass());
                if (expectedErrorMsgPart != null) {
                    assertTrue(cause.getMessage(), cause.getMessage().contains(expectedErrorMsgPart));
                }
            }
            else if (exceptionCase.getExpectedExceptionClass().equals(cause.getClass())) {
                wasThrown = true;

                //noinspection UseOfSystemOutOrSystemErr
                System.out.println();
                //noinspection UseOfSystemOutOrSystemErr
                e.printStackTrace(System.out);

                fail("Exception isn't expected here. Exception message: " + cause.getMessage());
            }
            else {
                throw e;
            }
        }
        finally {
            if (shouldOccur && !wasThrown) {
                fail(exceptionCase.getExpectedExceptionClass().getName() + " must be thrown.");
            }
        }
    }

    protected boolean annotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        Class<?> aClass = getClass();
        String methodName = "test" + getTestName(false);
        boolean methodChecked = false;
        while (aClass != null && aClass != Object.class) {
            if (aClass.getAnnotation(annotationClass) != null) return true;
            if (!methodChecked) {
                Method method = ReflectionUtil.getDeclaredMethod(aClass, methodName);
                if (method != null) {
                    if (method.getAnnotation(annotationClass) != null) return true;
                    methodChecked = true;
                }
            }
            aClass = aClass.getSuperclass();
        }
        return false;
    }

    @NotNull
    protected String getHomePath() {
        return PathManager.getHomePath().replace(File.separatorChar, '/');
    }

    public static void refreshRecursively(@NotNull VirtualFile file) {
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                file.getChildren();
                return true;
            }
        });
        file.refresh(false, true);
    }

    public static VirtualFile refreshAndFindFile(@NotNull final File file) {
        return UIUtil.invokeAndWaitIfNeeded(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
    }

    public static void waitForAppLeakingThreads(long timeout, @NotNull TimeUnit timeUnit) {
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                Application app = ApplicationManager.getApplication();
                if (app != null && !app.isDisposed()) {
                    FileBasedIndexImpl index = (FileBasedIndexImpl)app.getServiceIfCreated(FileBasedIndex.class);
                    if (index != null) {
                        index.getChangedFilesCollector().waitForVfsEventsExecuted(timeout, timeUnit, () -> {
                            dispatchAllEventsInIdeEventQueue();
                            return Unit.INSTANCE;
                        });
                    }

                    DocumentCommitThread commitThread = (DocumentCommitThread)app.getServiceIfCreated(DocumentCommitProcessor.class);
                    if (commitThread != null) {
                        commitThread.waitForAllCommits(timeout, timeUnit);
                    }
                }
            });
        } catch (Exception e) {
            LOG.warn(e);
        }
    }

    protected class TestDisposable implements Disposable {
        private volatile boolean myDisposed;

        public TestDisposable() {
        }

        @Override
        public void dispose() {
            myDisposed = true;
        }

        public boolean isDisposed() {
            return myDisposed;
        }

        @Override
        public String toString() {
            String testName = getTestName(false);
            return KtUsefulTestCase.this.getClass() + (StringUtil.isEmpty(testName) ? "" : ".test" + testName);
        }
    }
}
