// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.PathUtil;
import junit.framework.TestCase;
import kotlin.collections.CollectionsKt;
import kotlin.io.path.PathsKt;
import kotlin.jvm.functions.Function1;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;
import org.jetbrains.kotlin.idea.base.test.KotlinRoot;
import org.jetbrains.kotlin.idea.test.util.JetTestUtils;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestJdkKind;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;
import org.junit.Assert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.kotlin.idea.test.InTextDirectivesUtils.IGNORE_BACKEND_DIRECTIVE_PREFIX;
import static org.jetbrains.kotlin.idea.test.InTextDirectivesUtils.isIgnoredTarget;

public final class KotlinTestUtils {
    public static final String TEST_MODULE_NAME = "test-module";

    private static final boolean RUN_IGNORED_TESTS_AS_REGULAR =
            Boolean.getBoolean("org.jetbrains.kotlin.run.ignored.tests.as.regular");

    private static final boolean PRINT_STACKTRACE_FOR_IGNORED_TESTS =
            Boolean.getBoolean("org.jetbrains.kotlin.print.stacktrace.for.ignored.tests");

    private static final boolean DONT_IGNORE_TESTS_WORKING_ON_COMPATIBLE_BACKEND =
            Boolean.getBoolean("org.jetbrains.kotlin.dont.ignore.tests.working.on.compatible.backend");

    private static final boolean AUTOMATICALLY_UNMUTE_PASSED_TESTS = false;
    private static final boolean AUTOMATICALLY_MUTE_FAILED_TESTS = false;

    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile("^//\\s*[!]?([A-Z_]+)(:[ \\t]*(.*))?$", Pattern.MULTILINE);

    private KotlinTestUtils() {
    }

    @NotNull
    public static KotlinCoreEnvironment createEnvironmentWithJdkAndNullabilityAnnotationsFromIdea(
            @NotNull Disposable disposable,
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind
    ) {
        return KotlinCoreEnvironment.createForTests(
                disposable, newConfiguration(configurationKind, jdkKind, TestKotlinArtifacts.getJetbrainsAnnotations()),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
        );
    }

    @NotNull
    public static Ref<Disposable> allowProjectRootAccess(@NotNull UsefulTestCase testCase) {
        return allowRootAccess(testCase, KotlinRoot.REPO.getAbsolutePath());
    }

    @NotNull
    public static Ref<Disposable> allowRootAccess(@NotNull UsefulTestCase testCase, String... roots) {
        Disposable disposable = Disposer.newDisposable(testCase.getTestRootDisposable(), testCase.getClass().getName());
        VfsRootAccess.allowRootAccess(disposable, roots);
        return new Ref<>(disposable);
    }

    public static void disposeVfsRootAccess(@Nullable Ref<Disposable> vfsDisposableRef) {
        Disposable vfsDisposable = vfsDisposableRef != null ? vfsDisposableRef.get() : null;
        if (vfsDisposable != null && !Disposer.isDisposed(vfsDisposable)) {
            Disposer.dispose(vfsDisposable);
            vfsDisposableRef.set(null);
        }
    }

    public static File findMockJdkRtJar() {
        return new File(IdeaTestUtil.getMockJdk18Path().getAbsolutePath(), "jre/lib/rt.jar");
    }

    @NotNull
    public static File findAndroidSdk() {
        String androidSdkProp = System.getProperty("android.sdk");
        File androidSdkPropDir = androidSdkProp == null ? null : new File(androidSdkProp);

        String androidHomeEnv = System.getenv("ANDROID_HOME");
        File androidHomeEnvDir = androidHomeEnv == null ? null : new File(androidHomeEnv);

        String androidSdkRootEnv = System.getenv("ANDROID_SDK_ROOT");
        File androidSdkRootEnvDir = androidSdkRootEnv == null ? null : new File(androidSdkRootEnv);

        if (androidSdkPropDir != null) {
            return androidSdkPropDir;
        }

        if (androidHomeEnvDir != null) {
            return androidHomeEnvDir;
        }

        if (androidSdkRootEnvDir != null) {
            return androidSdkRootEnvDir;
        }

        throw new RuntimeException(
                "Unable to get a valid path from 'android.sdk' property (" + androidSdkProp + "), " +
                "please point it to the android SDK location");
    }

    public static String getAndroidSdkSystemIndependentPath() {
        return PathUtil.toSystemIndependentName(findAndroidSdk().getAbsolutePath());
    }

    public static void mkdirs(@NotNull File file) {
        if (file.isDirectory()) {
            return;
        }
        if (!file.mkdirs()) {
            if (file.exists()) {
                throw new IllegalStateException("Failed to create " + file + ": file exists and not a directory");
            }
            throw new IllegalStateException("Failed to create " + file);
        }
    }

    @NotNull
    public static File tmpDirForTest(@NotNull String testClassName, @NotNull String testName) throws IOException {
        return normalizeFile(FileUtil.createTempDirectory(testClassName, testName, false));
    }

    @NotNull
    public static File tmpDirForTest(TestCase test) throws IOException {
        return tmpDirForTest(test.getClass().getSimpleName(), test.getName());
    }

    @NotNull
    public static File tmpDir(String name) throws IOException {
        return normalizeFile(FileUtil.createTempDirectory(name, "", false));
    }

    @NotNull
    public static File tmpDirForReusableFolder(String name) throws IOException {
        return normalizeFile(FileUtil.createTempDirectory(new File(System.getProperty("java.io.tmpdir")), name, "", true));
    }

    private static File normalizeFile(File file) throws IOException {
        // Get canonical file to be sure that it's the same as inside the compiler,
        // for example, on Windows, if a canonical path contains any space from FileUtil.createTempDirectory we will get
        // a File with short names (8.3) in its path and it will break some normalization passes in tests.
        return file.getCanonicalFile();
    }

    @NotNull
    public static KtFile createFile(@NotNull @NonNls String name, @NotNull String text, @NotNull Project project) {
        String shortName = name.substring(name.lastIndexOf('/') + 1);
        shortName = shortName.substring(shortName.lastIndexOf('\\') + 1);
        LightVirtualFile virtualFile = new LightVirtualFile(shortName, KotlinLanguage.INSTANCE, StringUtilRt.convertLineSeparators(text));

        virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
        PsiFileFactoryImpl factory = (PsiFileFactoryImpl) PsiFileFactory.getInstance(project);
        return (KtFile) factory.trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false);
    }

    public static String doLoadFile(@NotNull File file) throws IOException {
        try {
            return FileUtil.loadFile(file, CharsetToolkit.UTF8, true);
        } catch (FileNotFoundException fileNotFoundException) {
            /*
             * Unfortunately, the FileNotFoundException will only show the relative path in it's exception message.
             * This clarifies the exception by showing the full path.
             */
            String messageWithFullPath = file.getAbsolutePath() + " (No such file or directory)";
            throw new IOException(
                    "Ensure you have your 'Working Directory' configured correctly as the root " +
                    "Kotlin project directory in your test configuration\n\t" +
                    messageWithFullPath,
                    fileNotFoundException);
        }
    }

    @NotNull
    public static CompilerConfiguration newConfiguration() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.put(CommonConfigurationKeys.MODULE_NAME, TEST_MODULE_NAME);

        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, new MessageCollector() {
            @Override
            public void clear() {
            }

            @Override
            public void report(
                    @NotNull CompilerMessageSeverity severity, @NotNull String message, @Nullable CompilerMessageSourceLocation location
            ) {
                if (severity == CompilerMessageSeverity.ERROR) {
                    String prefix = location == null
                                    ? ""
                                    : "(" + location.getPath() + ":" + location.getLine() + ":" + location.getColumn() + ") ";
                    throw new AssertionError(prefix + message);
                }
            }

            @Override
            public boolean hasErrors() {
                return false;
            }
        });

        return configuration;
    }

    @NotNull
    public static CompilerConfiguration newConfiguration(
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind,
            @NotNull File... extraClasspath
    ) {
        return newConfiguration(configurationKind, jdkKind, Arrays.asList(extraClasspath), Collections.emptyList());
    }

    @NotNull
    public static CompilerConfiguration newConfiguration(
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind,
            @NotNull List<File> classpath,
            @NotNull List<File> javaSource
    ) {
        CompilerConfiguration configuration = newConfiguration();
        JvmContentRootsKt.addJavaSourceRoots(configuration, javaSource);
        if (jdkKind == TestJdkKind.MOCK_JDK) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, findMockJdkRtJar());
            configuration.put(JVMConfigurationKeys.NO_JDK, true);
        } else {
            configuration.put(JVMConfigurationKeys.JDK_HOME, getCurrentProcessJdkHome());
        }

        if (configurationKind.getKotlinStdlib()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinStdlib());
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinScriptRuntime());
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinTest());
            configuration.put(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR, TestKotlinArtifacts.getKotlinCompiler());
        }

        if (configurationKind.getKotlinReflect()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinReflect());
        }

        JvmContentRootsKt.addJvmClasspathRoots(configuration, classpath);

        configuration.put(
                CLIConfigurationKeys.INTELLIJ_PLUGIN_ROOT,
                TestKotlinArtifacts.getKotlinCompiler().getAbsolutePath()
        );

        setupIdeaStandaloneExecution();

        return configuration;
    }

    private static void setupIdeaStandaloneExecution() {
        System.getProperties().setProperty("psi.incremental.reparse.depth.limit", "1000");
    }

    @NotNull
    public static File getCurrentProcessJdkHome() {
        return new File(System.getProperty("java.home"));
    }

    public static void assertEqualsToFile(@NotNull File expectedFile, @NotNull Editor editor) {
        assertEqualsToFile(expectedFile, editor, true);
    }

    public static void assertEqualsToFile(@NotNull File expectedFile, @NotNull Editor editor, Boolean enableSelectionTags) {
        Caret caret = editor.getCaretModel().getCurrentCaret();
        List<TagsTestDataUtil.TagInfo> tags = Lists.newArrayList(
                new TagsTestDataUtil.TagInfo<>(caret.getOffset(), true, "caret")
        );

        if (enableSelectionTags) {
            int selectionStart = caret.getSelectionStart();
            int selectionEnd = caret.getSelectionEnd();

            tags.add(new TagsTestDataUtil.TagInfo<>(selectionStart, true, "selection"));
            tags.add(new TagsTestDataUtil.TagInfo<>(selectionEnd, false, "selection"));
        }

        String afterText = TagsTestDataUtil.insertTagsInText(tags, editor.getDocument().getText(), (TagsTestDataUtil.TagInfo t) -> null);

        assertEqualsToFile(expectedFile, afterText);
    }

    public static void assertEqualsToFile(@NotNull Path expectedFile, @NotNull String actual) {
        assertEqualsToFile(expectedFile.toFile(), actual);
    }

    public static void assertEqualsToSibling(@NotNull Path originalFile, @NotNull String expectedExtension, @NotNull String actual) {
        String nameWithoutExtension = PathsKt.getNameWithoutExtension(originalFile);
        Path expectedFile = originalFile.resolveSibling(nameWithoutExtension + '.' + StringsKt.removePrefix(expectedExtension, "."));
        assertEqualsToFile(expectedFile, actual);
    }

    public static void assertEqualsToFile(@NotNull File expectedFile, @NotNull String actual) {
        assertEqualsToFile(expectedFile, actual, s -> s);
    }

    public static void assertEqualsToFile(@NotNull String message, @NotNull File expectedFile, @NotNull String actual) {
        assertEqualsToFile(message, expectedFile, actual, s -> s);
    }

    public static void assertEqualsToFile(
            @NotNull File expectedFile,
            @NotNull String actual,
            @NotNull Function1<String, String> sanitizer
    ) {
        assertEqualsToFile("Actual data differs from file content", expectedFile, actual, sanitizer);
    }

    public static void assertEqualsToFile(
            @NotNull String message,
            @NotNull File expectedFile,
            @NotNull String actual,
            @NotNull Function1<String, String> sanitizer
    ) {
        try {
            String actualText = JetTestUtils.trimTrailingWhitespacesAndAddNewlineAtEOF(StringUtil.convertLineSeparators(actual.trim()));

            if (!expectedFile.exists()) {
                FileUtil.writeToFile(expectedFile, actualText);
                Assert.fail("Expected data file did not exist. Generating: " + expectedFile);
            }
            String expected = FileUtil.loadFile(expectedFile, CharsetToolkit.UTF8, true);

            String expectedText = JetTestUtils.trimTrailingWhitespacesAndAddNewlineAtEOF(StringUtil.convertLineSeparators(expected.trim()));

            String sanitizedExpectedText = JetTestUtils.trimTrailingWhitespacesAndAddNewlineAtEOF(sanitizer.invoke(expectedText));
            String sanitizedActualText = JetTestUtils.trimTrailingWhitespacesAndAddNewlineAtEOF(sanitizer.invoke(actualText));

            if (!Objects.equals(sanitizedExpectedText, sanitizedActualText)) {
                throw new FileComparisonFailure(message + ": " + expectedFile.getName(),
                                                sanitizedExpectedText, sanitizedActualText, expectedFile.getAbsolutePath());
            }
        } catch (IOException e) {
            throw ExceptionUtilsKt.rethrow(e);
        }
    }

    @NotNull
    public static Directives parseDirectives(String expectedText) {
        return parseDirectives(expectedText, new Directives());
    }

    @NotNull
    public static Directives parseDirectives(String expectedText, @NotNull Directives directives) {
        Matcher directiveMatcher = DIRECTIVE_PATTERN.matcher(expectedText);
        while (directiveMatcher.find()) {
            String name = directiveMatcher.group(1);
            String value = directiveMatcher.group(3);
            directives.put(name, value);
        }
        return directives;
    }

    public static List<String> loadBeforeAfterText(String filePath) {
        String content;

        try {
            content = FileUtil.loadFile(new File(filePath), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> files = TestFiles.createTestFiles("", content, new TestFiles.TestFileFactoryNoModules<>() {
            @NotNull
            @Override
            public String create(@NotNull String fileName, @NotNull String text, @NotNull Directives directives) {
                int firstLineEnd = text.indexOf('\n');
                return StringUtil.trimTrailing(text.substring(firstLineEnd + 1));
            }
        });

        Assert.assertEquals("Exactly two files expected: ", 2, files.size());

        return files;
    }

    public static String getLastCommentedLines(@NotNull Document document) {
        List<CharSequence> resultLines = new ArrayList<>();
        for (int i = document.getLineCount() - 1; i >= 0; i--) {
            int lineStart = document.getLineStartOffset(i);
            int lineEnd = document.getLineEndOffset(i);
            if (document.getCharsSequence().subSequence(lineStart, lineEnd).toString().trim().isEmpty()) {
                continue;
            }

            if ("//".equals(document.getCharsSequence().subSequence(lineStart, lineStart + 2).toString())) {
                resultLines.add(document.getCharsSequence().subSequence(lineStart + 2, lineEnd));
            } else {
                break;
            }
        }
        Collections.reverse(resultLines);
        StringBuilder result = new StringBuilder();
        for (CharSequence line : resultLines) {
            result.append(line).append("\n");
        }
        result.delete(result.length() - 1, result.length());
        return result.toString();
    }

    public enum CommentType {
        ALL,
        LINE_COMMENT,
        BLOCK_COMMENT
    }

    @NotNull
    public static String getLastCommentInFile(@NotNull KtFile file) {
        return CollectionsKt.first(getLastCommentsInFile(file, CommentType.ALL, true));
    }

    @NotNull
    public static List<String> getLastCommentsInFile(@NotNull KtFile file, CommentType commentType, boolean assertMustExist) {
        PsiElement lastChild = file.getLastChild();
        if (lastChild != null && lastChild.getNode().getElementType().equals(KtTokens.WHITE_SPACE)) {
            lastChild = lastChild.getPrevSibling();
        }
        assert lastChild != null;

        List<String> comments = new ArrayList<>();

        while (true) {
            if (lastChild.getNode().getElementType().equals(KtTokens.BLOCK_COMMENT)) {
                if (commentType == CommentType.ALL || commentType == CommentType.BLOCK_COMMENT) {
                    String lastChildText = lastChild.getText();
                    comments.add(lastChildText.substring(2, lastChildText.length() - 2).trim());
                }
            } else if (lastChild.getNode().getElementType().equals(KtTokens.EOL_COMMENT)) {
                if (commentType == CommentType.ALL || commentType == CommentType.LINE_COMMENT) {
                    comments.add(lastChild.getText().substring(2).trim());
                }
            } else {
                break;
            }

            lastChild = lastChild.getPrevSibling();
        }

        if (comments.isEmpty() && assertMustExist) {
            throw new AssertionError(String.format(
                    "Test file '%s' should end in a comment of type %s; last node was: %s", file.getName(), commentType, lastChild));
        }

        return comments;
    }

    public static boolean compileJavaFiles(@NotNull Collection<File> files, List<String> options) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(new File(getCurrentProcessJdkHome(), "bin/javac").getPath());
        command.addAll(options);
        for (File file : files) {
            command.add(file.getPath());
        }

        try {
            Process process = new ProcessBuilder().command(command).inheritIO().start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            throw ExceptionUtilsKt.rethrow(e);
        }
    }

    public interface DoTest {
        void invoke(@NotNull String filePath) throws Exception;
    }

    public static void runTest(@NotNull DoTest test, @NotNull TestCase testCase, @TestDataFile String testDataFile) throws Exception {
        runTestImpl(testWithCustomIgnoreDirective(test, TargetBackend.ANY, IGNORE_BACKEND_DIRECTIVE_PREFIX), testCase, testDataFile);
    }

    // In this test runner version the `testDataFile` parameter is annotated by `TestDataFile`.
    // So only file paths passed to this parameter will be used in navigation actions, like "Navigate to testdata" and "Related Symbol..."
    public static void runTest(DoTest test, TestCase testCase, TargetBackend targetBackend, @TestDataFile String testDataFile)
            throws Exception {
        runTest0(test, testCase, targetBackend, testDataFile);
    }

    // In this test runner version, NONE of the parameters are annotated by `TestDataFile`.
    // So DevKit will use test name to determine related files in navigation actions, like "Navigate to testdata" and "Related Symbol..."
    //
    // Pro:
    // * in most cases, it shows all related files including generated js files, for example.
    // Cons:
    // * sometimes, for too common/general names, it shows many variants to navigate
    // * it adds an additional step for navigation -- you must choose an exact file to navigate
    public static void runTest0(DoTest test, TestCase testCase, TargetBackend targetBackend, String testDataFilePath) throws Exception {
        runTestImpl(testWithCustomIgnoreDirective(test, targetBackend, IGNORE_BACKEND_DIRECTIVE_PREFIX), testCase, testDataFilePath);
    }

    private static void runTestImpl(@NotNull DoTest test, @Nullable TestCase testCase, String testDataFilePath) throws Exception {
        String absoluteTestDataFilePath;

        File testDataFile = new File(testDataFilePath);
        if (testDataFile.isAbsolute()) {
            absoluteTestDataFilePath = testDataFilePath;
        } else {
            if ("true".equals(System.getProperty("kombo.compiler.tests.mode", "false"))) {
                absoluteTestDataFilePath = new File(TestKotlinArtifacts.jpsPluginTestData(testDataFilePath)).getAbsolutePath();
            } else {
                File testRoot = TestMetadataUtil.getTestRoot(testCase.getClass());
                if (testRoot == null) {
                    throw new IllegalStateException("@TestRoot annotation was not found on " + testCase.getName());
                }
                absoluteTestDataFilePath = new File(testRoot, testDataFilePath).getAbsolutePath();
            }
        }

        test.invoke(absoluteTestDataFilePath);
    }

    private static DoTest testWithCustomIgnoreDirective(DoTest test, TargetBackend targetBackend, String ignoreDirective) {
        return filePath -> {
            File testDataFile = new File(filePath);

            boolean isIgnored = isIgnoredTarget(targetBackend, testDataFile, ignoreDirective);

            if (DONT_IGNORE_TESTS_WORKING_ON_COMPATIBLE_BACKEND) {
                // Only ignore if it is ignored for both backends
                // Motivation: this backend works => all good, even if compatible backend fails
                // This backend fails, compatible works => need to know
                isIgnored &= isIgnoredTarget(targetBackend.getCompatibleWith(), testDataFile);
            }

            try {
                test.invoke(filePath);
            } catch (Throwable e) {
                if (!isIgnored && AUTOMATICALLY_MUTE_FAILED_TESTS) {
                    String text = doLoadFile(testDataFile);
                    String directive = ignoreDirective + targetBackend.name() + "\n";

                    String newText;
                    if (text.startsWith("// !")) {
                        StringBuilder prefixBuilder = new StringBuilder();
                        int l = 0;
                        while (text.startsWith("// !", l)) {
                            int r = text.indexOf("\n", l) + 1;
                            if (r <= 0) r = text.length();
                            prefixBuilder.append(text, l, r);
                            l = r;
                        }
                        prefixBuilder.append(directive);
                        prefixBuilder.append(text.substring(l));

                        newText = prefixBuilder.toString();
                    } else {
                        newText = directive + text;
                    }

                    if (!newText.equals(text)) {
                        System.err.println("\"" + directive + "\" was added to \"" + testDataFile + "\"");
                        FileUtil.writeToFile(testDataFile, newText);
                    }
                }

                if (RUN_IGNORED_TESTS_AS_REGULAR || !isIgnored) {
                    throw e;
                }

                if (PRINT_STACKTRACE_FOR_IGNORED_TESTS) {
                    e.printStackTrace();
                } else {
                    System.err.println("MUTED TEST with `" + ignoreDirective + "`");
                }
                return;
            }

            if (isIgnored) {
                if (AUTOMATICALLY_UNMUTE_PASSED_TESTS) {
                    String text = doLoadFile(testDataFile);
                    String directive = ignoreDirective + targetBackend.name();
                    String newText = Pattern.compile("^" + directive + "\n", Pattern.MULTILINE).matcher(text).replaceAll("");
                    if (!newText.equals(text)) {
                        System.err.println("\"" + directive + "\" was removed from \"" + testDataFile + "\"");
                        FileUtil.writeToFile(testDataFile, newText);
                    }
                }

                throw new AssertionError(
                        String.format("Looks like this test can be unmuted. Remove \"%s%s\" directive.", ignoreDirective, targetBackend));
            }
        };
    }

    public static String getTestsRoot(@NotNull Class<?> testCaseClass) {
        File testData = TestMetadataUtil.getTestData(testCaseClass);
        Assert.assertNotNull("No metadata for class: " + testCaseClass, testData);
        return testData.toString();
    }

    public static String toSlashEndingDirPath(@NotNull String path) {
        // Drop when LightPlatformCodeInsightTestCase#configureByFile gets rid of
        // `String fullPath = getTestDataPath() + relativePath;`
        return path.endsWith(File.separator) ? path : path + File.separatorChar;
    }

    /**
     * @return test data file name specified in the metadata of test method
     */
    @Nullable
    public static String getTestDataFileName(@NotNull Class<?> testCaseClass, @NotNull String testName) {
        try {
            Method method = testCaseClass.getMethod(testName);
            return getMethodMetadata(method);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private static String getMethodMetadata(Method method) {
        TestMetadata testMetadata = method.getAnnotation(TestMetadata.class);
        return (testMetadata != null) ? testMetadata.value() : null;
    }

    @NotNull
    public static File replaceExtension(@NotNull File file, @Nullable String newExtension) {
        return new File(file.getParentFile(), FileUtil.getNameWithoutExtension(file) + (newExtension == null ? "" : "." + newExtension));
    }

    public static boolean isMultiExtensionName(@NotNull String name) {
        int firstDotIndex = name.indexOf('.');
        if (firstDotIndex == -1) {
            return false;
        }
        // Several extension if name contains another dot
        return name.indexOf('.', firstDotIndex + 1) != -1;
    }
}
