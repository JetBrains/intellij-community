// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public abstract class EmmetPreviewTestBase extends BasePlatformTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    EmmetOptions.getInstance().setPreviewEnabled(true);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      EmmetOptions.getInstance().setPreviewEnabled(false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void assertPreview(@NotNull String previewContent) {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    EmmetPreviewHint previewHint = getPreview();
    assertNotNull(previewHint);
    assertEquals(previewContent, previewHint.getContent());
  }

  protected EmmetPreviewHint getPreview() {
    return EmmetPreviewHint.getExistingHint(myFixture.getEditor());
  }
}
