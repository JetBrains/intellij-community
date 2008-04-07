/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.source.parsing.FileTextParsing;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtil;

public class JavaFileElementType extends IStubFileElementType {
  public JavaFileElementType() {
    super("java.FILE", StdLanguages.JAVA);
  }

  public StubBuilder getBuilder() {
    return new JavaFileStubBuilder();
  }

  public ASTNode parseContents(ASTNode chameleon) {
    final CharSequence seq = ((LeafElement)chameleon).getInternedText();

    final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
    final JavaLexer lexer = new JavaLexer(PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi()));
    return FileTextParsing.parseFileText(manager, lexer,
                                         seq, 0, seq.length(), SharedImplUtil.findCharTableByTree(chameleon));
  }
  public boolean isParsable(CharSequence buffer, final Project project) {return true;}
}