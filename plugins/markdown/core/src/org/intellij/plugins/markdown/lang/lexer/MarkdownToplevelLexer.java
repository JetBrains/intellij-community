package org.intellij.plugins.markdown.lang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.ast.ASTNodeKt;
import org.intellij.markdown.ast.visitors.RecursiveVisitor;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.plugins.markdown.lang.MarkdownElementType;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MarkdownToplevelLexer extends LexerBase {
  private CharSequence myBuffer;
  private int myBufferStart;
  private int myBufferEnd;

  private int lastBufferHash = 0;

  private final List<IElementType> myLexemes = new ArrayList<>();
  private final List<Integer> myStartOffsets = new ArrayList<>();
  private final List<Integer> myEndOffsets = new ArrayList<>();

  private int myLexemeIndex;

  private @NotNull final MarkdownFlavourDescriptor flavour;

  public MarkdownToplevelLexer() {
    this(MarkdownParserManager.FLAVOUR);
  }

  public MarkdownToplevelLexer(@NotNull MarkdownFlavourDescriptor flavour) {
    this.flavour = flavour;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    final var bufferHash = buffer.hashCode();
    myBufferStart = startOffset;
    myBufferEnd = endOffset;
    if (bufferHash == lastBufferHash && buffer.equals(myBuffer)) {
      myLexemeIndex = initialState;
      return;
    }
    lastBufferHash = bufferHash;
    myBuffer = buffer;
    final var parsedTree = MarkdownParserManager.parseContent(buffer.subSequence(startOffset, endOffset), flavour);
    myLexemes.clear();
    myStartOffsets.clear();
    myEndOffsets.clear();
    ASTNodeKt.accept(parsedTree, new LexerBuildingVisitor());
    myLexemeIndex = 0;
  }

  @Override
  public int getState() {
    return myLexemeIndex;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    if (myLexemeIndex >= myLexemes.size()) {
      return null;
    }
    return myLexemes.get(myLexemeIndex);
  }

  @Override
  public int getTokenStart() {
    if (myLexemeIndex >= myLexemes.size()) {
      return myBufferEnd;
    }
    return myBufferStart + myStartOffsets.get(myLexemeIndex);
  }

  @Override
  public int getTokenEnd() {
    if (myLexemeIndex >= myLexemes.size()) {
      return myBufferEnd;
    }
    return myBufferStart + myEndOffsets.get(myLexemeIndex);
  }

  @Override
  public void advance() {
    myLexemeIndex++;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  private class LexerBuildingVisitor extends RecursiveVisitor {

    @Override
    public void visitNode(@NotNull ASTNode node) {
      ProgressManager.checkCanceled();
      if (node.getStartOffset() == node.getEndOffset()) {
        return;
      }
      final List<ASTNode> children = node.getChildren();
      if (children.isEmpty()) {
        myLexemes.add(MarkdownElementType.platformType(node.getType()));
        myStartOffsets.add(node.getStartOffset());
        myEndOffsets.add(node.getEndOffset());
      }
      else {
        super.visitNode(node);
      }
    }
  }
}
