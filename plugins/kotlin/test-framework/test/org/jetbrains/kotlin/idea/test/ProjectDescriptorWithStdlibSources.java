// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts;

public class ProjectDescriptorWithStdlibSources extends KotlinWithJdkAndRuntimeLightProjectDescriptor {
    @NotNull
    public static ProjectDescriptorWithStdlibSources getInstanceWithStdlibSources() {
        return new ProjectDescriptorWithStdlibSources();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model) {
        super.configureModule(module, model);

        Library library = model.getModuleLibraryTable().getLibraryByName(KotlinJdkAndLibraryProjectDescriptor.LIBRARY_NAME);
        assert library != null;
        Library.ModifiableModel modifiableModel = library.getModifiableModel();
        modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.getKotlinStdlibSources()), OrderRootType.SOURCES);
        modifiableModel.commit();
    }
}
