/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.DeclarationParsing;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Properties;

public class PsiJavaParserFacadeImpl implements PsiJavaParserFacade {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiJavaParserFacadeImpl");

  protected final PsiManagerEx myManager;
  protected static final Map<String, PsiPrimitiveType> ourPrimitiveTypesMap = new HashMap<String, PsiPrimitiveType>();
  protected PsiJavaFile myDummyJavaFile;

  public PsiJavaParserFacadeImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @NotNull
  public PsiAnnotation createAnnotationFromText(@NotNull String annotationText, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    CompositeElement annotationElement =
    getJavaParsingContext(holderElement).getDeclarationParsing().parseAnnotationFromText(myManager, annotationText, getLanguageLevel(context));
    if (annotationElement == null || annotationElement.getElementType() != JavaElementType.ANNOTATION) {
      throw new IncorrectOperationException("Incorrect annotation \"" + annotationText + "\".");
    }
    TreeUtil.addChildren(holderElement, annotationElement);
    return (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(annotationElement);
  }

  private LanguageLevel getLanguageLevel(final PsiElement context) {
    if (context == null) {
      return LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel();
    }
    return PsiUtil.getLanguageLevel(context);
  }

  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, null).getTreeElement();
    final LeafElement newElement = Factory.createLeafElement(
      TokenType.WHITE_SPACE,
      text,
      0,
      text.length(), holderElement.getCharTable()
    );
    TreeUtil.addChildren(holderElement, newElement);
    return newElement.getPsi();
  }

  @NotNull
  public PsiDocTag createDocTagFromText(@NotNull String docTagText, PsiElement context) throws IncorrectOperationException {
    StringBuilder buffer = new StringBuilder();
    buffer.append("/**\n");
    buffer.append(docTagText);
    buffer.append("\n */");
    PsiDocComment comment = createDocCommentFromText(buffer.toString(), context);
    return comment.getTags()[0];
  }

  @NotNull
  public PsiDocComment createDocCommentFromText(@NotNull String docCommentText, PsiElement context) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(docCommentText);
    buffer.append("void m();");
    final PsiMethod method = createMethodFromText(buffer.toString(), null);
    return method.getDocComment();
  }

  @NotNull
  public PsiClass createClassFromText(@NotNull String body, PsiElement context) throws IncorrectOperationException {
    @NonNls String fileText = "class _Dummy_ { " + body + " }";
    PsiJavaFile aFile = createDummyJavaFile(fileText);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class \"" + body + "\".");
    }
    return classes[0];
  }

  @NotNull
  public PsiField createFieldFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement).getDeclarationParsing().parseDeclarationText(myManager, LanguageLevelProjectExtension
      .getInstance(myManager.getProject()).getLanguageLevel(), text, DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != JavaElementType.FIELD) {
      throw new IncorrectOperationException("Incorrect field \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    return (PsiField)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  protected JavaParsingContext getJavaParsingContext (FileElement holderElement) {
    return new JavaParsingContext(holderElement.getCharTable(), LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel());
  }

  private static JavaParsingContext getJavaParsingContext (FileElement holderElement, LanguageLevel languageLevel) {
    return new JavaParsingContext(holderElement.getCharTable(), languageLevel);
  }

  @NotNull
  public PsiMethod createMethodFromText(@NotNull String text, PsiElement context, LanguageLevel level) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement, level).getDeclarationParsing().parseDeclarationText(myManager, level, text,
                                                                                                                DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != JavaElementType.METHOD) {
      throw new IncorrectOperationException("Incorrect method \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    return (PsiMethod)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  @NotNull
  public final PsiMethod createMethodFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    return createMethodFromText(text, context, LanguageLevelProjectExtension.getInstance(myManager.getProject()).getLanguageLevel());
  }

  @NotNull
  public PsiParameter createParameterFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    CompositeElement param = getJavaParsingContext(holderElement).getDeclarationParsing().parseParameterText(text);
    if (param == null) {
      throw new IncorrectOperationException("Incorrect parameter \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, param);
    return (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(param);
  }

  @NotNull
  public PsiType createTypeFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, false);
  }
 
  protected PsiType createTypeInner(final String text, final PsiElement context, boolean markAsCopy) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = ourPrimitiveTypesMap.get(text);
    if (primitiveType != null) return primitiveType;
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    CompositeElement typeElement = Parsing.parseTypeText(myManager, text, 0, text.length(), holderElement.getCharTable());
    if (typeElement == null) {
      throw new IncorrectOperationException("Incorrect type \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, typeElement);
    if (markAsCopy) {
      holderElement.acceptTree(new GeneratedMarkerVisitor());
    }
    PsiTypeElement psiTypeElement = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(typeElement);
    if (psiTypeElement == null) {
      throw new IncorrectOperationException("PSI is null for element "+typeElement);
    }
    return psiTypeElement.getType();
  }

  @NotNull
  public PsiCodeBlock createCodeBlockFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    CompositeElement treeElement = getJavaParsingContext(holderElement).getStatementParsing().parseCodeBlockText(myManager, text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect code block \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    return (PsiCodeBlock)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiStatement createStatementFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(treeHolder).getStatementParsing().parseStatementText(text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect statement \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiExpression createExpressionFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new JavaDummyHolder(myManager, context).getTreeElement();
    final CompositeElement treeElement = ExpressionParsing.parseExpressionText(myManager, text, 0,
                                                                               text.length(), treeHolder.getCharTable());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect expression \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  protected PsiJavaFile createDummyJavaFile(String text) {
    String ext = StdFileTypes.JAVA.getDefaultExtension();
    @NonNls String fileName = "_Dummy_." + ext;
    FileType type = StdFileTypes.JAVA;

    return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject()).createFileFromText(type, fileName, text, 0, text.length());
  }

  @NotNull
  public PsiTypeParameter createTypeParameterFromText(@NotNull String text, PsiElement context)
    throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(holderElement).getDeclarationParsing().parseTypeParameterText(text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect type parameter \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    return (PsiTypeParameter)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiComment createCommentFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiElement[] children = aFile.getChildren();
    for (PsiElement aChildren : children) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
        }
        PsiComment comment = (PsiComment)aChildren;
        new JavaDummyHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }
    throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
  }

  @NotNull
  public PsiEnumConstant createEnumConstantFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement).getDeclarationParsing().parseEnumConstantText(text);
    if (decl == null || decl.getElementType() != JavaElementType.ENUM_CONSTANT) {
      throw new IncorrectOperationException("Incorrect enum constant text \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    return (PsiEnumConstant)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  @NotNull
  public PsiCatchSection createCatchSection(@NotNull PsiClassType exceptionType,
                                            @NotNull String exceptionName,
                                            PsiElement context) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("catch (");
    buffer.append(exceptionType.getCanonicalText());
    buffer.append(" ").append(exceptionName).append("){}");
    String catchSectionText = buffer.toString();
    final FileElement holderElement = new JavaDummyHolder(myManager, context).getTreeElement();
    TreeElement catchSection = getJavaParsingContext(holderElement).getStatementParsing().parseCatchSectionText(catchSectionText);
    LOG.assertTrue(catchSection != null && catchSection.getElementType() == JavaElementType.CATCH_SECTION);
    TreeUtil.addChildren(holderElement, catchSection);
    PsiCatchSection psiCatchSection = (PsiCatchSection)SourceTreeToPsiMap.treeElementToPsi(catchSection);

    setupCatchBlock(exceptionName, context, psiCatchSection);
    return (PsiCatchSection)myManager.getCodeStyleManager().reformat(psiCatchSection);
  }

  private void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection psiCatchSection)
    throws IncorrectOperationException {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(FileTemplateManager.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        FileTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }
    PsiCodeBlock codeBlockFromText;
    try {
      String catchBody = catchBodyTemplate.getText(props);
      codeBlockFromText = createCodeBlockFromText("{\n" + catchBody + "\n}", null);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template",e);
    }
    psiCatchSection.getCatchBlock().replace(codeBlockFromText);
  }
}
