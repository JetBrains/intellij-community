package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiJavaFileImpl extends PsiJavaFileBaseImpl {
  public PsiJavaFileImpl(FileViewProvider file) {
    super(JAVA_FILE, JAVA_FILE, file);
  }

  public String toString(){
    return "PsiJavaFile:" + getName();
  }

  public Lexer createLexer() {
    return new JavaLexer(getLanguageLevel());
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  public void setPackageName(final String packageName) throws IncorrectOperationException {
    PsiPackageStatement packageStatement = getPackageStatement();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    if (packageStatement != null) {
      if (packageName.length() > 0) {
        packageStatement.replace(factory.createPackageStatement(packageName));
      }
      else {
        packageStatement.delete();
      }
    }
    else {
      if (packageName.length() > 0) {
        add(factory.createPackageStatement(packageName));
      }
    }
  }
}
