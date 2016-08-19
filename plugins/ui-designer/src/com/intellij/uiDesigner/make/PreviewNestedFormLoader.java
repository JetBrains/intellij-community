/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.compiler.PsiClassWriter;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.actions.PreviewFormAction;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.CodeGenerationException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author yole
 */
public class PreviewNestedFormLoader extends PsiNestedFormLoader {
  private final String myTempPath;
  private final InstrumentationClassFinder myFinder;
  private final Set<String> myGeneratedClasses = new HashSet<>();

  public PreviewNestedFormLoader(final Module module, final String tempPath, final InstrumentationClassFinder finder) {
    super(module);
    myTempPath = tempPath;
    myFinder = finder;
  }

  public LwRootContainer loadForm(String formFileName) throws Exception {
    LwRootContainer rootContainer = super.loadForm(formFileName);
    if (!myGeneratedClasses.contains(formFileName)) {
      myGeneratedClasses.add(formFileName);
      String generatedClassName = "FormPreviewFrame" + myGeneratedClasses.size();
      PreviewFormAction.setPreviewBindings(rootContainer, generatedClassName);
      generateStubClass(rootContainer, generatedClassName);
    }
    return rootContainer;
  }

  private void generateStubClass(final LwRootContainer rootContainer, final String generatedClassName) throws IOException,
                                                                                                              CodeGenerationException {
    @NonNls ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V1_1, Opcodes.ACC_PUBLIC, generatedClassName, null, "java/lang/Object", ArrayUtil.EMPTY_STRING_ARRAY);

    cw.visitField(Opcodes.ACC_PUBLIC, PreviewFormAction.PREVIEW_BINDING_FIELD, "Ljavax/swing/JComponent;", null, null);

    @NonNls MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();

    ByteArrayInputStream bais = new ByteArrayInputStream(cw.toByteArray());
    AsmCodeGenerator acg = new AsmCodeGenerator(rootContainer, myFinder, this, true, new PsiClassWriter(myModule));
    byte[] data = acg.patchClass(bais);
    FormErrorInfo[] errors = acg.getErrors();
    if (errors.length > 0) {
      throw new CodeGenerationException(errors [0].getComponentId(), errors [0].getErrorMessage());
    }

    FileUtil.writeToFile(new File(myTempPath, generatedClassName + ".class"), data);
  }
}
