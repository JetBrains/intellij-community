/*
 * Copyright (c) 2005, Your Corporation. All Rights Reserved.
 */
package com.jetbrains.python.psi;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.MayHaveStubsInside;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyFile extends PyElement, PsiFile {
    @NotNull
    FileType getFileType();

    @PsiCached
    @MayHaveStubsInside
    List<PyStatement> getStatements();

    PythonLanguage getPyLanguage();

    List<PyClass> getTopLevelClasses();
    List<PyFunction> getTopLevelFunctions();
}
