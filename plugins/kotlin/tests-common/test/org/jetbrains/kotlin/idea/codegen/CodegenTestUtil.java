// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codegen;

import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.utils.ExceptionUtilsKt;
import org.jetbrains.kotlin.utils.StringsKt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CodegenTestUtil {
    private CodegenTestUtil() {
    }

    public static void compileJava(
            @NotNull List<String> fileNames,
            @NotNull List<String> additionalClasspath,
            @NotNull List<String> additionalOptions,
            @NotNull File outDirectory
    ) {
        try {
            List<String> options = prepareJavacOptions(additionalClasspath, additionalOptions, outDirectory);
            KotlinTestUtils.compileJavaFiles(CollectionsKt.map(fileNames, File::new), options);
        }
        catch (IOException e) {
            throw ExceptionUtilsKt.rethrow(e);
        }
    }
    @NotNull
    public static List<String> prepareJavacOptions(
            @NotNull List<String> additionalClasspath,
            @NotNull List<String> additionalOptions,
            @NotNull File outDirectory
    ) {
        List<String> classpath = new ArrayList<>();
        classpath.add(KotlinArtifacts.getInstance().getKotlinStdlib().getPath());
        classpath.add(KotlinArtifacts.getInstance().getKotlinReflect().getPath());
        classpath.add(KotlinArtifacts.getInstance().getJetbrainsAnnotations().getPath());
        classpath.addAll(additionalClasspath);

        List<String> options = new ArrayList<>(Arrays.asList(
                "-classpath", StringsKt.join(classpath, File.pathSeparator),
                "-d", outDirectory.getPath()
        ));
        options.addAll(additionalOptions);
        return options;
    }
}
