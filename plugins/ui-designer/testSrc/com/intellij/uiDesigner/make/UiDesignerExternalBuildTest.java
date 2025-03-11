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
import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.uiDesigner.GuiDesignerConfiguration;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UiDesignerExternalBuildTest extends ArtifactCompilerTestCase {

  //IDEA-94779
  public void testCopyFormsRuntimeToArtifact() throws IOException {
    VirtualFile file = createFile("src/A.java", "class A{}");
    VirtualFile srcRoot = file.getParent();
    Module module = addModule("a", srcRoot);
    Artifact a = addArtifact(root().module(module));
    make(a);
    assertOutput(a, fs().file("A.class"));

    copyClassWithForm(srcRoot);
    make(a);
    File outputDir = VfsUtilCore.virtualToIoFile(getOutputDir(a));
    assertTrue(new File(outputDir, "A.class").exists());
    assertTrue(new File(outputDir, "B.class").exists());

    assertTrue(new File(outputDir, AbstractLayout.class.getName().replace('.', '/') + ".class").exists());
  }

  public void testRecompileFormOnFormFileRecompilation() throws IOException {
    VirtualFile javaFileWithoutForm = createFile("src/A.java", "class A{}");
    VirtualFile srcRoot = javaFileWithoutForm.getParent();
    copyClassWithForm(srcRoot);
    VirtualFile javaFile = srcRoot.findChild("B.java");
    assertNotNull(javaFile);
    VirtualFile formFile = srcRoot.findChild("B.form");
    assertNotNull(formFile);

    GuiDesignerConfiguration.getInstance(myProject).COPY_FORMS_RUNTIME_TO_OUTPUT = false;
    Module module = addModule("a", srcRoot);
    make(module);

    compile(true, javaFileWithoutForm).assertGenerated("A.class");
    compile(true, javaFile).assertGenerated("B.class");
    compile(true, formFile).assertGenerated("B.class");
  }

  private static void copyClassWithForm(VirtualFile srcRoot) throws IOException {
    File dir = PathManagerEx.findFileUnderCommunityHome("plugins/ui-designer/testData/build/copyFormsRuntimeToArtifact");
    FileUtil.copyDir(dir, VfsUtilCore.virtualToIoFile(srcRoot));
    srcRoot.refresh(false, false);
  }
  
  public void testDisableAndEnableSwingUiDesignerPlugin() throws IOException {
    VirtualFile javaFileWithoutForm = createFile("src/A.java", "class A{}");
    VirtualFile srcRoot = javaFileWithoutForm.getParent();
    copyClassWithForm(srcRoot);
    Module module = addModule("a", srcRoot);
    make(module);
    assertFalse(isInstrumented(module, "A.class"));
    assertTrue(isInstrumented(module, "B.class"));

    List<CompileServerPlugin> withoutUiDesigner = ContainerUtil.filter(CompileServerPlugin.EP_NAME.getExtensionList(), e -> !e.getClasspath().contains("java-guiForms-jps.jar"));
    Disposable disableUiDesigner = Disposer.newDisposable();
    try {
      ExtensionTestUtil.maskExtensions(CompileServerPlugin.EP_NAME, withoutUiDesigner, disableUiDesigner);

      make(module);
      assertFalse(isInstrumented(module, "B.class"));
    }
    finally {
      Disposer.dispose(disableUiDesigner);
    }

    /* currently it's necessary to change something to skip fast up-to-date check; in production, enabling/disabling UI Designer plugin 
       requires restart, so this won't be a problem; however, it would be better to automatically skip fast up-to-date check if the set
       of enabled instrumenters changes
    */
    changeFile(javaFileWithoutForm, "class A{ void foo(){} }");
    
    make(module);
    assertTrue(isInstrumented(module, "B.class"));
  }

  private static boolean isInstrumented(Module module, String outputFileRelativePath) throws IOException {
    File file = new File(getOutputDir(module), outputFileRelativePath);
    String text = FileUtil.loadFile(file);
    return text.contains("$$$setupUI$$$");
  }
}
