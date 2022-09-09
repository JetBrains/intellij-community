// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.fileType;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShFileType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class ShFileTypeDetectorTest extends BasePlatformTestCase {

  public void testShebangFileDetect1() {
    doTypingTest("#!/usr/bin/env <caret>\n", "zsh", ShFileType.class);
  }
  public void testShebangFileDetect2() {
    doTypingTest("#!  /usr/bin/env <caret>\n", "zsh", ShFileType.class);
  }
  public void testShebangFileDetect3() {
    doTypingTest("#!/bin/<caret> \n", "bash", ShFileType.class);
  }
  public void testShebangFileDetect4() {
    doTypingTest("#! /bin/<caret>   \n", "sh", ShFileType.class);
  }
  public void testShebangFileDetect5() {
    doTypingTest("#!/bin/<caret> -x\n", "sh", ShFileType.class);
  }
  public void testShebangFileDetect6() {
    doTypingTest("#!C:/AppData/Git/usr/bin/<caret>.exe\n", "sh", ShFileType.class);
  }
  public void testInvalidShebangFileDetect1() {
    doTypingTest("#!<caret>\n", " ", PlainTextFileType.class);
  }
  public void testInvalidShebangFileDetect2() {
    doTypingTest("/usr/bin<caret>\n", " ", PlainTextFileType.class);
  }

  private void doTypingTest(String initialFileContent, String insertedContent, Class<?> expectedFileType) {
    PsiFile psiFile = myFixture.configureByText("a", initialFileContent);
    assertTrue(psiFile.getFileType() instanceof PlainTextFileType);
    myFixture.type(insertedContent);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    FileDocumentManager.getInstance().saveDocument(documentManager.getDocument(psiFile));
    ApplicationManager.getApplication().runWriteAction(() -> {
      FileTypeManagerEx fileTypeManagerEx = (FileTypeManagerEx)FileTypeManager.getInstance();
      fileTypeManagerEx.makeFileTypesChange("sh file type detector test", EmptyRunnable.getInstance());
    });
    assertTrue(expectedFileType.isInstance(myFixture.getFile().getFileType()));
  }
}