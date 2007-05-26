package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.Block;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.util.IncorrectOperationException;

public class CodeFormatterFacade implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade");

  private CodeStyleSettings mySettings;
  private Helper myHelper;
  private CommentFormatter myCommentFormatter;

  public CodeFormatterFacade(CodeStyleSettings settings, Helper helper) {
    mySettings = settings;
    myHelper = helper;
    myCommentFormatter = new CommentFormatter(helper.getProject());
  }

  private TextRange formatComments(ASTNode element, final TextRange range) {
    if (!mySettings.ENABLE_JAVADOC_FORMATTING || element.getPsi().getContainingFile().getLanguage() != StdLanguages.JAVA) {
      return range;
    }

    return formatCommentsInner(element, range);
  }

  private TextRange formatCommentsInner(ASTNode element, final TextRange range) {
    TextRange result = range;

    PsiElement psi;

    // check for RepositoryTreeElement is optimization
    if (element instanceof RepositoryTreeElement &&
        (psi = element.getPsi()) instanceof PsiDocCommentOwner) {
      final TextRange elementRange = element.getTextRange();

      if (range.contains(elementRange)) {
        myCommentFormatter.process(element);
        final TextRange newRange = element.getTextRange();
        result = new TextRange(range.getStartOffset(), range.getEndOffset() + newRange.getLength() - elementRange.getLength());
      }

      // optimization, does not seek PsiDocComment inside fields / methods or out of range
      if (psi instanceof PsiField ||
          psi instanceof PsiMethod ||
          range.getEndOffset() < elementRange.getStartOffset()
         ) {
        return result;
      }
    }

    if (element instanceof CompositeElement) {
      ASTNode current = element.getFirstChildNode();

      while (current != null) {
        // we expand the chameleons here for effectiveness
        if (current instanceof ChameleonElement) {
          ASTNode next = current.getTreeNext();
          final ASTNode astNode = ChameleonTransforming.transform((ChameleonElement)element);
          if (astNode == null) current = next;
          else current = astNode;
        }
        result = formatCommentsInner(current, result);
        current = current.getTreeNext();
      }
    }
    return result;
  }

  public ASTNode process(ASTNode element, int parent_indent) {
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
    final FormattingModelBuilder builder = file.getLanguage().getEffectiveFormattingModelBuilder(file);
    if (builder != null) {
      TextRange range = element.getTextRange();
      return processRange(element, range.getStartOffset(), range.getEndOffset());
    }

    return element;
  }

  public ASTNode processRange(final ASTNode element, final int startOffset, final int endOffset) {
    final FileType fileType = myHelper.getFileType();

    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    final PsiFile file = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile();
    final FormattingModelBuilder builder = file.getLanguage().getEffectiveFormattingModelBuilder(file);
    final Document document = file.getViewProvider().getDocument();
    final RangeMarker rangeMarker = document != null && endOffset < document.getTextLength()? document.createRangeMarker(startOffset, endOffset):null;

    if (builder != null) {
      TextRange range = formatComments(element, new TextRange(startOffset, endOffset));
      //final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final PsiFile containingFile = psiElement.getContainingFile();
      final FormattingModel model = builder.createModel(psiElement, mySettings);
      if (containingFile.getTextLength() > 0) {
        try {
          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, true);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      /*
       */
      if (!psiElement.isValid()) {
        if (rangeMarker != null) {
          final PsiElement at = file.findElementAt(rangeMarker.getStartOffset());
          assert psiElement.getClass().isInstance(at);
          return at.getNode();
        } else {
          assert false;
        }
      }

//      return SourceTreeToPsiMap.psiElementToTree(pointer.getElement());

    }

    return element;
  }

  public void processText(final PsiFile file, final int startOffset, final int endOffset) {
    processText(file, startOffset, endOffset, true);
  }

  private void processText(final PsiFile file, final int startOffset, final int endOffset, boolean headWhitespace) {
    final FileType fileType = myHelper.getFileType();

    final FormattingModelBuilder builder = file.getLanguage().getEffectiveFormattingModelBuilder(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          TextRange range = formatComments(file.getNode(), new TextRange(startOffset, endOffset));
          final PostprocessReformattingAspect component = file.getProject().getComponent(PostprocessReformattingAspect.class);
          component.doPostponedFormatting(file.getViewProvider());
          Block rootBlock= builder.createModel(file, mySettings).getRootBlock();
          Project project = file.getProject();
          final FormattingModel model = new DocumentBasedFormattingModel(rootBlock,
                                                                         PsiDocumentManager.getInstance(project).getDocument(file),
                                                                         project, mySettings, fileType, file);

          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, headWhitespace);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  public void processTextWithoutHeadWhitespace(final PsiFile file, final int startOffset, final int endOffset) {
    final FileType fileType = myHelper.getFileType();

    final FormattingModelBuilder builder = file.getLanguage().getEffectiveFormattingModelBuilder(file);

    if (builder != null) {
      if (file.getTextLength() > 0) {
        try {
          TextRange range = formatComments(file.getNode(), new TextRange(startOffset, endOffset));
          FormattingModel originalModel = builder.createModel(file, mySettings);
          Project project = file.getProject();
          final FormattingModel model = new DocumentBasedFormattingModel(originalModel.getRootBlock(),
            PsiDocumentManager.getInstance(project).getDocument(file),
            project, mySettings, fileType, file);

          FormatterEx.getInstanceEx().format(model, mySettings,
                                             mySettings.getIndentOptions(fileType), range, false);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }
}

