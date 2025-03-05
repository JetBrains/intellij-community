// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.workspaceModel.ide.legacyBridge.impl.java.JavaModuleTypeUtils.JAVA_MODULE_ENTITY_TYPE_ID_NAME;

public class KotlinLightProjectDescriptor extends LightProjectDescriptor {

    protected KotlinLightProjectDescriptor() {
    }
    
    public static final KotlinLightProjectDescriptor INSTANCE = new KotlinLightProjectDescriptor();

    @NotNull
    @Override
    public String getModuleTypeId() {
        return JAVA_MODULE_ENTITY_TYPE_ID_NAME;
    }

    @Override
    public @Nullable Sdk getSdk() {
        return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        addDefaultLibraries(model);
        configureModule(module, model);
    }

    protected void addDefaultLibraries(@NotNull ModifiableRootModel model) {
        DefaultLightProjectDescriptor.addJetBrainsAnnotations(model);
    }

    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model) {
    }
}
