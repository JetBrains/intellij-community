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
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.SerializationManager;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubSerializer;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class JavaFileElementType extends IStubFileElementType<PsiJavaFileStub> implements StubSerializer<PsiJavaFileStub> {
  public JavaFileElementType() {
    super("java.FILE", StdLanguages.JAVA);
    SerializationManager.getInstance().registerSerializer(this);
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

  public String getExternalId() {
    return "java.FILE";
  }

  public void serialize(final PsiJavaFileStub stub, final DataOutputStream dataStream, final PersistentStringEnumerator nameStorage)
      throws IOException {
    DataInputOutputUtil.writeNAME(dataStream, stub.getPackageName(), nameStorage);
  }

  public PsiJavaFileStub deserialize(final DataInputStream dataStream,
                                     final StubElement parentStub, final PersistentStringEnumerator nameStorage) throws IOException {
    String packName = DataInputOutputUtil.readNAME(dataStream, nameStorage);
    return new PsiJavaFileStubImpl(packName);
  }

  public void indexStub(final PsiJavaFileStub stub, final IndexSink sink) {
  }
}