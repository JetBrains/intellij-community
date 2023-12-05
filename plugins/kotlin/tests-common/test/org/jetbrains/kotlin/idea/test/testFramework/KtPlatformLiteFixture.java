// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test.testFramework;

import com.intellij.core.CoreEncodingProjectManager;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.pico.DefaultPicoContainer;

public abstract class KtPlatformLiteFixture extends KtUsefulTestCase {
    protected MockProjectEx myProject;

    public static MockApplication getApplication() {
        return (MockApplication) ApplicationManager.getApplication();
    }

    public void initApplication() {
        MockApplication instance = new MockApplication(getTestRootDisposable());
        ApplicationManager.setApplication(instance, FileTypeManager::getInstance, getTestRootDisposable());
        getApplication().registerService(EncodingManager.class, CoreEncodingProjectManager.class);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            clearFields(this);
            myProject = null;
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T registerComponentInstance(DefaultPicoContainer container, Class<T> key, T implementation) {
        Object old = container.getComponentInstance(key);
        container.unregisterComponent(key);
        container.registerComponentInstance(key, implementation);
        return (T)old;
    }
}
