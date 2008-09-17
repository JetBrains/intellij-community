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
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.impl.source.parsing.FileTextParsing;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaFileElementType extends IStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 1;

  public JavaFileElementType() {
    super("java.FILE", StdLanguages.JAVA);
  }

  public StubBuilder getBuilder() {
    return new JavaFileStubBuilder();
  }

  public int getStubVersion() {
    return STUB_VERSION;
  }

  public ASTNode parseContents(ASTNode chameleon) {
    final CharSequence seq = ((LeafElement)chameleon).getInternedText();

    final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
    final JavaLexer lexer = new JavaLexer(PsiUtil.getLanguageLevel(TreeUtil.getFileElement((TreeElement)chameleon).getPsi()));
    return FileTextParsing.parseFileText(manager, lexer, seq, 0, seq.length(), SharedImplUtil.findCharTableByTree(chameleon));
  }
  public boolean isParsable(CharSequence buffer, final Project project) {return true;}

  public String getExternalId() {
    return "java.FILE";
  }

  public void serialize(final PsiJavaFileStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    dataStream.writeName(stub.getPackageName());
  }

  public PsiJavaFileStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    StringRef packName = dataStream.readName();
    return new PsiJavaFileStubImpl(packName, compiled);
  }

  public void indexStub(final PsiJavaFileStub stub, final IndexSink sink) {
  }
}