// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.framework;

import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinIcons;
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility;
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion;
import org.jetbrains.kotlin.idea.projectConfiguration.JavaRuntimeLibraryDescription;

import javax.swing.*;
import java.util.List;
import java.util.regex.Pattern;

public class JavaRuntimePresentationProvider extends LibraryPresentationProvider<LibraryVersionProperties> {
    private static final Pattern KOTLIN_LIBRARY_JAR_PATTERN = Pattern.compile("kotlinx?-(reflect|stdlib-jdk.|test|atomicfu|coroutines|datetime|serialization)-.*\\.jar");

    public static JavaRuntimePresentationProvider getInstance() {
        return LibraryPresentationProvider.EP_NAME.findExtension(JavaRuntimePresentationProvider.class);
    }

    protected JavaRuntimePresentationProvider() {
        super(JavaRuntimeLibraryDescription.Companion.getKOTLIN_JAVA_RUNTIME_KIND());
    }

    @Override
    public @Nullable Icon getIcon(@Nullable LibraryVersionProperties properties) {
        return KotlinIcons.SMALL_LOGO;
    }

    @Override
    public @Nullable LibraryVersionProperties detect(@NotNull List<VirtualFile> classesRoots) {
        if (classesRoots.size() > 5) return null;

        IdeKotlinVersion version = KotlinJvmStdlibDetectorFacility.INSTANCE.getStdlibVersion(classesRoots);
        if (version != null) return new LibraryVersionProperties(version.getArtifactVersion());

        IdeKotlinVersion kotlinLibraryVersion = getKotlinLibraryVersion(classesRoots);
        if (kotlinLibraryVersion != null) {
            return new LibraryVersionProperties(kotlinLibraryVersion.getArtifactVersion());
        }
        return null;
    }

    private static IdeKotlinVersion getKotlinLibraryVersion(@NotNull List<VirtualFile> roots) {
        for (VirtualFile root : roots) {
            String name = root.getName();
            if (KOTLIN_LIBRARY_JAR_PATTERN.matcher(name).matches()) {
                IdeKotlinVersion kotlinVersion = IdeKotlinVersion.fromManifest(root);
                return kotlinVersion;
            }
        }
        return null;
    }
}
