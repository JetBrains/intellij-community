/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.make;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.actions.PreviewFormAction;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.compiler.CodeGenerationException;
import com.intellij.uiDesigner.compiler.FormErrorInfo;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * @author yole
 */
public class PreviewNestedFormLoader extends PsiNestedFormLoader {
  private final String myTempPath;
  private final ClassLoader myLoader;
  private final Set<String> myGeneratedClasses = new HashSet<String>();

  public PreviewNestedFormLoader(final Module module, final String tempPath, final ClassLoader loader) {
    super(module);
    myTempPath = tempPath;
    myLoader = loader;
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
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V");
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    cw.visitEnd();

    ByteArrayInputStream bais = new ByteArrayInputStream(cw.toByteArray());
    AsmCodeGenerator acg = new AsmCodeGenerator(rootContainer, myLoader, this, true);
    byte[] data = acg.patchClass(bais);
    FormErrorInfo[] errors = acg.getErrors();
    if (errors.length > 0) {
      throw new CodeGenerationException(errors [0].getComponentId(), errors [0].getErrorMessage());
    }

    //noinspection HardCodedStringLiteral
    FileOutputStream fos = new FileOutputStream(new File(myTempPath, generatedClassName + ".class"));
    try {
      fos.write(data);
    }
    finally {
      fos.close();
    }
  }
}
