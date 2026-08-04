// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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

  private int myBufferHash = 0;

  private final List<IElementType> myLexemes = new ArrayList<>();
  private final IntList myStartOffsets = new IntArrayList();
  private final IntList myEndOffsets = new IntArrayList();
  private final IntList myStates = new IntArrayList();

  private int myLexemeIndex;

  private final @NotNull MarkdownFlavourDescriptor flavour;

  public MarkdownToplevelLexer() {
    this(MarkdownParserManager.FLAVOUR);
  }

  public MarkdownToplevelLexer(@NotNull MarkdownFlavourDescriptor flavour) {
    this.flavour = flavour;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    final var bufferHash = buffer.hashCode();
    if (bufferHash == myBufferHash && startOffset == myBufferStart && buffer.equals(myBuffer)) {
      myLexemeIndex = 0;
      return;
    }
    myBufferHash = bufferHash;
    myBuffer = buffer;
    myBufferStart = startOffset;
    myBufferEnd = endOffset;
    final var parsedTree = MarkdownParserManager.parseContent(buffer.subSequence(startOffset, endOffset), flavour);
    myLexemes.clear();
    myStartOffsets.clear();
    myEndOffsets.clear();
    myStates.clear();
    for (ASTNode child : parsedTree.getChildren()) {
      ASTNodeKt.accept(child, new LexerBuildingVisitor());
    }
    myLexemeIndex = 0;
  }

  @Override
  public int getState() {
    return myLexemeIndex < myStates.size() ? myStates.getInt(myLexemeIndex) : 1;
  }

  @Override
  public @Nullable IElementType getTokenType() {
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
    return myBufferStart + myStartOffsets.getInt(myLexemeIndex);
  }

  @Override
  public int getTokenEnd() {
    if (myLexemeIndex >= myLexemes.size()) {
      return myBufferEnd;
    }
    return myBufferStart + myEndOffsets.getInt(myLexemeIndex);
  }

  @Override
  public void advance() {
    myLexemeIndex++;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  private class LexerBuildingVisitor extends RecursiveVisitor {
    private boolean myFirstLeaf = true;

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
        myStates.add(myFirstLeaf ? 0 : 1);
        myFirstLeaf = false;
      }
      else {
        super.visitNode(node);
      }
    }
  }
}
