// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codegen;

import com.intellij.openapi.project.Project;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.codegen.ClassFileFactory;
import org.jetbrains.kotlin.codegen.GeneratedClassLoader;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.idea.TestHelperGeneratorKt;
import org.jetbrains.kotlin.idea.test.*;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestJdkKind;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class CodegenTestCase extends KotlinBaseTest<KotlinBaseTest.TestFile> {
    protected KotlinCoreEnvironment myEnvironment;
    protected List<KtFile> myFiles;
    protected ClassFileFactory classFileFactory;
    protected GeneratedClassLoader initializedClassLoader;

    protected final void createEnvironmentWithMockJdkAndIdeaAnnotations(
            @NotNull ConfigurationKind configurationKind,
            @NotNull File... javaSourceRoots
    ) {
        if (myEnvironment != null) {
            throw new IllegalStateException("must not set up myEnvironment twice");
        }

        CompilerConfiguration configuration = createConfiguration(
                configurationKind,
                TestJdkKind.MOCK_JDK,
                getBackend(),
                Collections.singletonList(TestKotlinArtifacts.getJetbrainsAnnotations()),
                ArraysKt.filterNotNull(javaSourceRoots),
                Collections.emptyList()
        );

        myEnvironment = KotlinCoreEnvironment.createForTests(
                getTestRootDisposable(), configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES
        );
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            myFiles = null;
            myEnvironment = null;
            classFileFactory = null;

            if (initializedClassLoader != null) {
                initializedClassLoader.dispose();
                initializedClassLoader = null;
            }
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    protected void loadMultiFiles(@NotNull List<TestFile> files) {
        myFiles = loadMultiFiles(files, myEnvironment.getProject());
    }

    @NotNull
    public static List<KtFile> loadMultiFiles(@NotNull List<TestFile> files, @NotNull Project project) {
        Collections.sort(files);

        List<KtFile> ktFiles = new ArrayList<>(files.size());
        for (TestFile file : files) {
            if (file.name.endsWith(".kt") || file.name.endsWith(".kts")) {
                // `rangesToDiagnosticNames` parameter is not-null only for diagnostic tests, it's using for lazy diagnostics
                String content = CheckerTestUtil.INSTANCE.parseDiagnosedRanges(file.content, new ArrayList<>(0), null);
                ktFiles.add(KotlinTestUtils.createFile(file.name, content, project));
            }
        }

        return ktFiles;
    }

    @NotNull
    @Override
    protected TargetBackend getBackend() {
        return TargetBackend.JVM;
    }

    @Override
    protected void doTest(@NotNull String filePath) throws Exception {
        File file = new File(filePath);

        String expectedText = KotlinTestUtils.doLoadFile(file);
        List<TestFile> testFiles = createTestFilesFromFile(file, expectedText);

        doMultiFileTest(file, testFiles);
    }

    @Override
    @NotNull
    protected List<TestFile> createTestFilesFromFile(@NotNull File file, @NotNull String expectedText) {
        return createTestFilesFromFile(file, expectedText, parseDirectivesPerFiles(), getBackend());
    }

    @NotNull
    public static List<TestFile> createTestFilesFromFile(
            @NotNull File file,
            @NotNull String expectedText,
            boolean parseDirectivesPerFiles,
            @NotNull TargetBackend backend
    ) {
        List<TestFile> testFiles = TestFiles.createTestFiles(file.getName(), expectedText, new TestFiles.TestFileFactoryNoModules<>() {
            @NotNull
            @Override
            public TestFile create(@NotNull String fileName, @NotNull String text, @NotNull Directives directives) {
                return new TestFile(fileName, text, directives);
            }
        }, false, parseDirectivesPerFiles);
        if (InTextDirectivesUtils.isDirectiveDefined(expectedText, "WITH_HELPERS")) {
            testFiles.add(new TestFile("CodegenTestHelpers.kt", TestHelperGeneratorKt.createTextForCodegenTestHelpers(backend)));
        }
        return testFiles;
    }


    @NotNull
    protected File getJavaSourcesOutputDirectory() {
        return createTempDirectory("java-files");
    }

    @NotNull
    private static File createTempDirectory(String prefix) {
        try {
            return KotlinTestUtils.tmpDir(prefix);
        } catch (IOException e) {
            throw ExceptionUtilsKt.rethrow(e);
        }
    }

    @Nullable
    protected File writeJavaFiles(@NotNull List<TestFile> files) {
        List<TestFile> javaFiles = CollectionsKt.filter(files, file -> file.name.endsWith(".java"));
        if (javaFiles.isEmpty()) return null;

        File dir = getJavaSourcesOutputDirectory();

        for (TestFile testFile : javaFiles) {
            File file = new File(dir, testFile.name);
            KotlinTestUtils.mkdirs(file.getParentFile());
            FilesKt.writeText(file, testFile.content, Charsets.UTF_8);
        }

        return dir;
    }
}
