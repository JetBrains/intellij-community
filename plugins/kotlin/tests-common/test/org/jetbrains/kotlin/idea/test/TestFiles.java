// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.kotlin.idea.test.KotlinTestUtils.parseDirectives;

public final class TestFiles {
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

    private static final Pattern MODULE_PATTERN = Pattern.compile("//\\s*MODULE:\\s*([^()\\r\\n]+)(?:\\(([^()]+(?:" + MODULE_DELIMITER + "[^()]+)*)\\))?\\s*(?:\\(([^()]+(?:" + MODULE_DELIMITER + "[^()]+)*)\\))?\\R");
    private static final Pattern FILE_PATTERN = Pattern.compile("//\\s*FILE:\\s*(.*)\\R");

    @NotNull
    public static <M extends KotlinBaseTest.TestModule, F> List<F> createTestFiles(
            @Nullable String testFileName,
            String expectedText,
            TestFileFactory<M, ? extends F> factory
    ) {
        Map<String, M> modules = new HashMap<>();
        List<F> testFiles = new ArrayList<>();
        Matcher fileMatcher = FILE_PATTERN.matcher(expectedText);
        Matcher moduleMatcher = MODULE_PATTERN.matcher(expectedText);

        boolean fileFound = fileMatcher.find();
        boolean moduleFound = moduleMatcher.find();
        if (!fileFound && !moduleFound) {
            assert testFileName != null : "testFileName should not be null if no FILE directive defined";
            // One file
            testFiles.add(factory.createFile(null, testFileName, expectedText, parseDirectives(expectedText)));
        } else {
            Directives allFilesOrCommonPrefixDirectives = parseDirectives(expectedText);
            int processedChars = 0;
            M module = null;

            // Many files
            while (true) {
                if (moduleFound) {
                    String moduleName = moduleMatcher.group(1);
                    String moduleDependencies = moduleMatcher.group(2);
                    String moduleFriends = moduleMatcher.group(3);
                    if (moduleName != null) {
                        moduleName = moduleName.trim();
                        module = factory.createModule(moduleName, parseModuleList(moduleDependencies), parseModuleList(moduleFriends));
                        M oldValue = modules.put(moduleName, module);
                        assert oldValue == null : "Module with name " + moduleName + " already present in file";
                    }
                }

                boolean nextModuleExists = moduleMatcher.find();
                moduleFound = nextModuleExists;
                while (true) {
                    String fileName = fileMatcher.group(1);
                    int start = processedChars;

                    boolean nextFileExists = fileMatcher.find();
                    int end;
                    if (nextFileExists && nextModuleExists) {
                        end = Math.min(fileMatcher.start(), moduleMatcher.start());
                    }
                    else if (nextFileExists) {
                        end = fileMatcher.start();
                    }
                    else {
                        end = expectedText.length();
                    }
                    String fileText = expectedText.substring(start, end);
                    testFiles.add(factory.createFile(module, fileName, fileText, allFilesOrCommonPrefixDirectives));
                    processedChars = end;
                    if (!nextFileExists && !nextModuleExists) break;
                    if (nextModuleExists && fileMatcher.start() > moduleMatcher.start()) break;
                }
                if (!nextModuleExists) break;
            }
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
