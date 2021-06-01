// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.test;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.TestHelperGeneratorKt;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.kotlin.test.InTextDirectivesUtils.isDirectiveDefined;
import static org.jetbrains.kotlin.test.KotlinTestUtils.parseDirectives;

public class TestFiles {
    /**
     * Syntax:
     * <p>
     * // MODULE: name(dependency1, dependency2, ...)
     * <p>
     * // FILE: name
     * <p>
     * Several files may follow one module
     */
    private static final String MODULE_DELIMITER = ",\\s*";

    private static final Pattern FILE_OR_MODULE_PATTERN = Pattern.compile(
            "(?://\\s*MODULE:\\s*([^()\\n]+)(?:\\(([^()]+(?:" +
            MODULE_DELIMITER +
            "[^()]+)*)\\))?\\s*(?:\\(([^()]+(?:" +
            MODULE_DELIMITER +
            "[^()]+)*)\\))?\\s*)?" +
            "//\\s*FILE:\\s*(.*)$", Pattern.MULTILINE);

    private static final Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\r\\n|\\r|\\n");

    @NotNull
    public static <M extends KotlinBaseTest.TestModule, F> List<F> createTestFiles(
            @Nullable String testFileName,
            String expectedText,
            TestFileFactory<M, ? extends F> factory
    ) {
        return createTestFiles(testFileName, expectedText, factory, false, false);
    }

    @NotNull
    public static <M extends KotlinBaseTest.TestModule, F> List<F> createTestFiles(
            String testFileName, String expectedText, TestFileFactory<M, ? extends F> factory,
            boolean preserveLocations, boolean parseDirectivesPerFile
    ) {
        Map<String, M> modules = new HashMap<>();
        List<F> testFiles = new ArrayList<>();
        Matcher matcher = FILE_OR_MODULE_PATTERN.matcher(expectedText);
        boolean hasModules = false;
        String commonPrefixOrWholeFile;
        if (!matcher.find()) {
            assert testFileName != null : "testFileName should not be null if no FILE directive defined";
            // One file
            testFiles.add(factory.createFile(null, testFileName, expectedText, parseDirectives(expectedText)));
            commonPrefixOrWholeFile = expectedText;
        } else {
            Directives allFilesOrCommonPrefixDirectives = parseDirectivesPerFile ? null : parseDirectives(expectedText);
            int processedChars = 0;
            M module = null;
            boolean firstFileProcessed = false;
            commonPrefixOrWholeFile = expectedText.substring(0, matcher.start());

            // Many files
            while (true) {
                String moduleName = matcher.group(1);
                String moduleDependencies = matcher.group(2);
                String moduleFriends = matcher.group(3);
                if (moduleName != null) {
                    moduleName = moduleName.trim();
                    hasModules = true;
                    module = factory.createModule(moduleName, parseModuleList(moduleDependencies), parseModuleList(moduleFriends));
                    M oldValue = modules.put(moduleName, module);
                    assert oldValue == null : "Module with name " + moduleName + " already present in file";
                }

                String fileName = matcher.group(4);
                int start = processedChars;

                boolean nextFileExists = matcher.find();
                int end;
                if (nextFileExists) {
                    end = matcher.start();
                } else {
                    end = expectedText.length();
                }
                String fileText = preserveLocations ?
                                  substringKeepingLocations(expectedText, start, end) :
                                  expectedText.substring(start, end);


                String expectedText1 = firstFileProcessed ? commonPrefixOrWholeFile + fileText : fileText;
                testFiles.add(factory.createFile(module, fileName, fileText,
                                                 parseDirectivesPerFile ?
                                                 parseDirectives(expectedText1)
                                                                        : allFilesOrCommonPrefixDirectives));
                processedChars = end;
                firstFileProcessed = true;
                if (!nextFileExists) break;
            }
        }

        if (isDirectiveDefined(expectedText, "WITH_COROUTINES")) {
            M supportModule = hasModules ? factory.createModule("support", Collections.emptyList(), Collections.emptyList()) : null;
            if (supportModule != null) {
                M oldValue = modules.put(supportModule.name, supportModule);
                assert oldValue == null : "Module with name " + supportModule.name + " already present in file";
            }

            boolean isReleaseCoroutines = !isDirectiveDefined(expectedText, "!LANGUAGE: -ReleaseCoroutines");

            boolean checkStateMachine = isDirectiveDefined(expectedText, "CHECK_STATE_MACHINE");
            boolean checkTailCallOptimization = isDirectiveDefined(expectedText, "CHECK_TAIL_CALL_OPTIMIZATION");

            testFiles.add(
                    factory.createFile(
                            supportModule,
                            "CoroutineUtil.kt",
                            TestHelperGeneratorKt.createTextForCoroutineHelpers(
                                    isReleaseCoroutines, checkStateMachine, checkTailCallOptimization),
                            parseDirectives(commonPrefixOrWholeFile)
                    ));
        }

        for (M module : modules.values()) {
            if (module != null) {
                module.getDependencies().addAll(ContainerUtil.map(module.dependenciesSymbols, name -> {
                    M dep = modules.get(name);
                    assert dep != null : "Dependency not found:" + name + "for module " + module.name;
                    return dep;
                }));

                module.getFriends().addAll(ContainerUtil.map(module.friendsSymbols, name -> {
                    M dep = modules.get(name);
                    assert dep != null : "Dependency not found:" + name + "for module " + module.name;
                    return dep;
                }));
            }
        }


        return testFiles;
    }

    private static String substringKeepingLocations(String string, int start, int end) {
        Matcher matcher = LINE_SEPARATOR_PATTERN.matcher(string);
        StringBuilder prefix = new StringBuilder();
        int lastLineOffset = 0;
        while (matcher.find()) {
            if (matcher.end() > start) {
                break;
            }

            lastLineOffset = matcher.end();
            prefix.append('\n');
        }

        while (lastLineOffset++ < start) {
            prefix.append(' ');
        }

        return prefix + string.substring(start, end);
    }

    private static List<String> parseModuleList(@Nullable String dependencies) {
        if (dependencies == null) return Collections.emptyList();
        return kotlin.text.StringsKt.split(dependencies, Pattern.compile(MODULE_DELIMITER), 0);
    }

    public interface TestFileFactory<M, F> {
        F createFile(@Nullable M module, @NotNull String fileName, @NotNull String text, @NotNull Directives directives);

        M createModule(@NotNull String name, @NotNull List<String> dependencies, @NotNull List<String> friends);
    }

    public static abstract class TestFileFactoryNoModules<F> implements TestFileFactory<KotlinBaseTest.TestModule, F> {
        @Override
        public final F createFile(
                @Nullable KotlinBaseTest.TestModule module,
                @NotNull String fileName,
                @NotNull String text,
                @NotNull Directives directives
        ) {
            return create(fileName, text, directives);
        }

        @NotNull
        public abstract F create(@NotNull String fileName, @NotNull String text, @NotNull Directives directives);

        @Override
        public KotlinBaseTest.TestModule createModule(
                @NotNull String name,
                @NotNull List<String> dependencies,
                @NotNull List<String> friends
        ) {
            return null;
        }
    }
}
