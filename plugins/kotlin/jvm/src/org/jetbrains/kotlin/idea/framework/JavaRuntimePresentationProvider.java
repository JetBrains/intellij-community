// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.framework;

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

public class JavaRuntimePresentationProvider extends LibraryPresentationProvider<LibraryVersionProperties> {
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
        IdeKotlinVersion version = KotlinJvmStdlibDetectorFacility.INSTANCE.getStdlibVersion(classesRoots);
        return version == null ? null : new LibraryVersionProperties(version.getArtifactVersion());
    }
}
