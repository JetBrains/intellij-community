// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.editor.ResourceBundleFileStructureViewElement;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;

public class ResourceBundleGetFilesNullTest extends BasePlatformTestCase {

  public void testGetFilesNull() {
    final PsiFile file = myFixture.addFileToProject("empty.properties", "");

    final ResourceBundleImpl bundle = new ResourceBundleImpl((PropertiesFile)file);
    bundle.invalidate();

    ResourceBundleFileStructureViewElement myElement = new ResourceBundleFileStructureViewElement(bundle, () -> true);

    Assert.assertNull("Should return null on an invalidated resource bundle", myElement.getFiles());
  }
}
