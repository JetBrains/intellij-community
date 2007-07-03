package com.intellij.lang.xml;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewBuilderProvider;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.lang.*;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.documentation.XmlDocumentationProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 10:59:22 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLLanguage extends CompositeLanguage {
  private FoldingBuilder myFoldingBuilder;
  protected static final CDATAOnAnyEncodedPolicy CDATA_ON_ANY_ENCODED_POLICY = new CDATAOnAnyEncodedPolicy();
  protected static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();
  private final FormattingModelBuilder myFormattingModelBuilder;
  private XmlFindUsagesProvider myXmlFindUsagesProvider;

  public XMLLanguage() {
    this("XML", "text/xml");
  }

  protected XMLLanguage(@NonNls String name, @NonNls String... mime) {
    super(name, mime);
    myFormattingModelBuilder = new FormattingModelBuilder() {
      @NotNull
      public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
        final ASTNode root = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
        final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(element.getContainingFile());
        return new PsiBasedFormattingModel(element.getContainingFile(),
                                           new XmlBlock(root, null, null, new XmlPolicy(settings, documentModel), null, null),
                                           documentModel);
      }
    };
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    return new XmlFileHighlighter();
  }

  public XmlPsiPolicy getPsiPolicy() {
    return CDATA_ON_ANY_ENCODED_POLICY;
  }

  public ParserDefinition getParserDefinition() {
    return new XMLParserDefinition();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    if (myXmlFindUsagesProvider == null) myXmlFindUsagesProvider = new XmlFindUsagesProvider();
    return myXmlFindUsagesProvider;
  }

  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return new SurroundDescriptor[]{new XmlSurroundDescriptor()};
  }

  public Commenter getCommenter() {
    return new XmlCommenter();
  }

  @NotNull
  public TokenSet getReadableTextContainerElements() {
    return TokenSet.orSet(super.getReadableTextContainerElements(), TokenSet.create(XmlElementType.XML_CDATA,
                                                                                    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                                                                    XmlTokenType.XML_DATA_CHARACTERS));
  }

  public FoldingBuilder getFoldingBuilder() {
    if (myFoldingBuilder == null) myFoldingBuilder = new XmlFoldingBuilder();
    return myFoldingBuilder;
  }

  public FormattingModelBuilder getFormattingModelBuilder() {
    return myFormattingModelBuilder;
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      StructureViewBuilder builder = getStructureViewBuilderForExtensions(psiFile);
      if (builder != null) {
        return builder;
      }

      for (XmlStructureViewBuilderProvider xmlStructureViewBuilderProvider : getStructureViewBuilderProviders()) {
        final StructureViewBuilder structureViewBuilder = xmlStructureViewBuilderProvider.createStructureViewBuilder((XmlFile)psiFile);
        if (structureViewBuilder != null) {
          return structureViewBuilder;
        }
      }

      return new TreeBasedStructureViewBuilder() {
        @NotNull
        public StructureViewModel createStructureViewModel() {
          return new XmlStructureViewTreeModel((XmlFile)psiFile);
        }
      };
    }
    else {
      return null;
    }
  }

  private static XmlStructureViewBuilderProvider[] getStructureViewBuilderProviders() {
    return (XmlStructureViewBuilderProvider[])Extensions.getExtensions(XmlStructureViewBuilderProvider.EXTENSION_POINT_NAME);
  }

  private StructureViewBuilder getStructureViewBuilderForExtensions(final PsiFile psiFile) {
    for (Language language : getLanguageExtensionsForFile(psiFile)) {
      final StructureViewBuilder builder = language.getStructureViewBuilder(psiFile);
      if (builder != null) {
        return builder;
      }
    }
    return null;
  }

  @Nullable
  public ExternalAnnotator getExternalAnnotator() {
    return new XMLExternalAnnotator();
  }

  public FileViewProvider createViewProvider(final VirtualFile file, final PsiManager manager, final boolean physical) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      return new SingleRootFileViewProvider(manager, file, physical);
    }

    return new XmlFileViewProvider(manager, file, physical, this);
  }

  protected DocumentationProvider createDocumentationProvider() {
    return new XmlDocumentationProvider();
  }
}
