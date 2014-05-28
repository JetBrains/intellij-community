/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.make;

import com.intellij.compiler.artifacts.ArtifactCompilerTestCase;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.uiDesigner.core.AbstractLayout;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class UiDesignerExternalBuildTest extends ArtifactCompilerTestCase {

  //IDEA-94779
  public void testCopyFormsRuntimeToArtifact() throws IOException {
    VirtualFile file = createFile("src/A.java", "class A{}");
    VirtualFile srcRoot = file.getParent();
    Module module = addModule("a", srcRoot);
    Artifact a = addArtifact(root().module(module));
    make(a);
    assertOutput(a, fs().file("A.class"));

    File dir = PathManagerEx.findFileUnderCommunityHome("plugins/ui-designer/testData/build/copyFormsRuntimeToArtifact");
    FileUtil.copyDir(dir, VfsUtilCore.virtualToIoFile(srcRoot));
    srcRoot.refresh(false, false);
    make(a);
    File outputDir = VfsUtilCore.virtualToIoFile(getOutputDir(a));
    assertTrue(new File(outputDir, "A.class").exists());
    assertTrue(new File(outputDir, "B.class").exists());

    assertTrue(new File(outputDir, AbstractLayout.class.getName().replace('.', '/') + ".class").exists());
  }

}
