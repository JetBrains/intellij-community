package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
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
    PsiManager manager = getManager();
    PsiPackageStatement packageStatement = getPackageStatement();
    if (packageStatement != null) {
      if (packageName.length() > 0) {
        PsiJavaCodeReferenceElement packageRef = packageStatement.getPackageReference();
        PsiJavaCodeReferenceElement newRef =
          manager.getElementFactory().createReferenceElementByFQClassName(packageName, getResolveScope());
        packageRef.replace(newRef);
      }
      else {
        packageStatement.delete();
      }
    }
    else {
      if (packageName.length() > 0) {
        add(manager.getElementFactory().createPackageStatement(packageName));
      }
    }
  }
}
