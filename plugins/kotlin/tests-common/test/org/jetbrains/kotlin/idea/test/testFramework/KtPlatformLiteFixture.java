// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test.testFramework;

import com.intellij.core.CoreEncodingProjectManager;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.picocontainer.MutablePicoContainer;

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
        super.tearDown();
        clearFields(this);
        myProject = null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T registerComponentInstance(MutablePicoContainer container, Class<T> key, T implementation) {
        Object old = container.getComponentInstance(key);
        container.unregisterComponent(key);
        container.registerComponentInstance(key, implementation);
        return (T)old;
    }
}
