/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public class TemplateDataElementType extends IFileElementType {
  private final IElementType myTemplateElementType;
  private final IElementType myOuterElementType;

  public TemplateDataElementType(@NonNls String debugName, Language language, IElementType templateElementType, IElementType outerElementType) {
    super(debugName, language);
    myTemplateElementType = templateElementType;
    myOuterElementType = outerElementType;
  }

  protected Lexer createBaseLexer(TemplateLanguageFileViewProvider viewProvider) {
    return LanguageParserDefinitions.INSTANCE.forLanguage(viewProvider.getBaseLanguage()).createLexer(viewProvider.getManager().getProject());
  }

  protected LanguageFileType createTemplateFakeFileType(final Language language) {
    return new TemplateFileType(language);
  }

  public ASTNode parseContents(ASTNode chameleon) {
    final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
    final FileElement treeElement = new JavaDummyHolder(((TreeElement)chameleon).getManager(), null, table).getTreeElement();
    final PsiFile file = (PsiFile)TreeUtil.getFileElement((TreeElement)chameleon).getPsi();
    PsiFile originalFile = file.getOriginalFile();
    if (originalFile == null) originalFile = file;

    final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)originalFile.getViewProvider();

    final Language language = viewProvider.getTemplateDataLanguage();
    final CharSequence chars = ((LeafElement)chameleon).getInternedText();

    final Lexer baseLexer = createBaseLexer(viewProvider);
    final CharSequence templateText = createTemplateText(chars, baseLexer);
    final PsiFile templateFile = createFromText(language, templateText, file.getManager());

    final TreeElement parsed = ((PsiFileImpl)templateFile).calcTreeElement();
    ChameleonTransforming.transformChildren(parsed, false);

    Lexer langLexer = LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(file.getProject());
    final Lexer lexer = new MergingLexerAdapter(
      new TemplateBlackAndWhiteLexer(createBaseLexer(viewProvider), langLexer, myTemplateElementType, myOuterElementType),
      TokenSet.create(myTemplateElementType, myOuterElementType));
    lexer.start(chars, 0, chars.length(),0);
    insertOuters(parsed, lexer, table);

    if (parsed != null) TreeUtil.addChildren(treeElement, parsed.getFirstChildNode());

    treeElement.clearCaches();
    treeElement.subtreeChanged();
    return treeElement.getFirstChildNode();
  }

  private CharSequence createTemplateText(CharSequence buf, Lexer lexer) {
    StringBuilder result = new StringBuilder(buf.length());
    lexer.start(buf, 0, buf.length(), 0);

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenType() == myTemplateElementType) {
        result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
      }
      lexer.advance();
    }

    return result;
  }

  private void insertOuters(TreeElement root, Lexer lexer, final CharTable table) {
    TreePatcher patcher = new SimpleTreePatcher();

    int treeOffset = 0;
    LeafElement leaf = TreeUtil.findFirstLeaf(root);
    while (lexer.getTokenType() != null) {
      IElementType tt = lexer.getTokenType();
      if (tt != myTemplateElementType) {
        while (leaf != null && treeOffset < lexer.getTokenStart()) {
          treeOffset += leaf.getTextLength();
          if (treeOffset > lexer.getTokenStart()) {
            if (leaf instanceof ChameleonElement) {
              final ASTNode transformed = ChameleonTransforming.transform(leaf);
              treeOffset -= leaf.getTextLength();
              leaf = TreeUtil.findFirstLeaf(transformed);
              continue;
            }
            leaf = patcher.split(leaf, leaf.getTextLength() - (treeOffset - lexer.getTokenStart()), table);
            treeOffset = lexer.getTokenStart();
          }
          leaf = (LeafElement)TreeUtil.nextLeaf(leaf);
        }

        if (leaf == null) break;

        final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(lexer, table, myOuterElementType);
        patcher.insert(leaf.getTreeParent(), leaf, newLeaf);
        leaf.getTreeParent().subtreeChanged();
        leaf = newLeaf;
      }
      lexer.advance();
    }

    if (lexer.getTokenType() != null) {
      assert lexer.getTokenType() != myTemplateElementType;
      final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(lexer, table, myOuterElementType);
      TreeUtil.addChildren((CompositeElement)root, newLeaf);
      ((CompositeElement)root).subtreeChanged();
    }
  }

  protected OuterLanguageElementImpl createOuterLanguageElement(final Lexer lexer, final CharTable table,
                                                                final IElementType outerElementType) {
    return new OuterLanguageElementImpl(outerElementType, lexer.getBufferSequence(),
                                                                            lexer.getTokenStart(), lexer.getTokenEnd(), table);
  }

  private PsiFile createFromText(final Language language, CharSequence text, PsiManager manager) {
    @NonNls
    final LightVirtualFile virtualFile = new LightVirtualFile("foo", createTemplateFakeFileType(language), text, LocalTimeCounter.currentTime());

    FileViewProvider viewProvider = new SingleRootFileViewProvider(manager, virtualFile, false) {
      @NotNull
      public Language getBaseLanguage() {
        return language;
      }
    };

    return viewProvider.getPsi(language);
  }

  protected static class TemplateFileType extends LanguageFileType {
    private final Language myLanguage;

    public TemplateFileType(final Language language) {
      super(language);
      myLanguage = language;
    }

    @NotNull
    public String getDefaultExtension() {
      return "";
    }

    @NotNull
    @NonNls
    public String getDescription() {
      return "fake for language" + myLanguage.getID();
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }

    @NotNull
    @NonNls
    public String getName() {
      return myLanguage.getID();
    }

  }
}
