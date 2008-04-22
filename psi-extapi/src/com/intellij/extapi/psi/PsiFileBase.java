package com.intellij.extapi.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 25, 2005
 * Time: 9:40:47 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class PsiFileBase extends PsiFileImpl {
  @NotNull private Language myLanguage;
  @NotNull private ParserDefinition myParserDefinition;

  protected PsiFileBase(FileViewProvider viewProvider, @NotNull Language language) {
    super(viewProvider);
    initLanguage(language);
  }

  private void initLanguage(final Language language) {
    myLanguage = language;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (parserDefinition == null) {
      throw new RuntimeException("PsiFileBase: language.getParserDefinition() returned null.");
    }
    myParserDefinition = parserDefinition;
    final IFileElementType nodeType = parserDefinition.getFileNodeType();
    init(nodeType, nodeType);
  }

  @NotNull
  public final Language getLanguage() {
    return myLanguage;
  }

  protected final FileElement createFileElement(final CharSequence docText) {
    //final ParserDefinition parserDefinition = myLanguage.getParserDefinition();
    //if (parserDefinition != null && parserDefinition.createParser(getProject()) != PsiUtil.NULL_PARSER) {
    //  return _createFileElement(docText, myLanguage, getProject(), parserDefinition);
    //}
    return super.createFileElement(docText);
  }

  //private static FileElement _createFileElement(final CharSequence docText,
  //                                              final Language language,
  //                                              Project project,
  //                                              final ParserDefinition parserDefinition) {
  //  final PsiParser parser = parserDefinition.createParser(project);
  //  final IElementType root = parserDefinition.getFileNodeType();
  //  final PsiBuilderImpl builder = new PsiBuilderImpl(language, project, null, docText);
  //  final FileElement fileRoot = (FileElement)parser.parse(root, builder);
  //  LOG.assertTrue(fileRoot.getElementType() == root,
  //                 "Parsing file text returns rootElement with type different from declared in parser definition");
  //  return fileRoot;
  //}

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @NotNull
  public ParserDefinition getParserDefinition() {
    return myParserDefinition;
  }
}
