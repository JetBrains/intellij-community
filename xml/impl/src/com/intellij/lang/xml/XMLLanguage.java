package com.intellij.lang.xml;

import com.intellij.codeInsight.hint.api.impls.XmlParameterInfoHandler;
import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewBuilderProvider;
import com.intellij.ide.structureView.impl.xml.XmlStructureViewTreeModel;
import com.intellij.lang.Commenter;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
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
  private XmlFindUsagesProvider myXmlFindUsagesProvider;

  public XMLLanguage() {
    this("XML", "text/xml");

    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new XmlFileHighlighter();
      }
    });
  }

  protected XMLLanguage(@NonNls String name, @NonNls String... mime) {
    super(name, mime);
  }

  public XmlPsiPolicy getPsiPolicy() {
    return CDATA_ON_ANY_ENCODED_POLICY;
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    if (myXmlFindUsagesProvider == null) myXmlFindUsagesProvider = new XmlFindUsagesProvider();
    return myXmlFindUsagesProvider;
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

  public FileViewProvider createViewProvider(final VirtualFile file, final PsiManager manager, final boolean physical) {
    if (SingleRootFileViewProvider.isTooLarge(file)) {
      return new SingleRootFileViewProvider(manager, file, physical);
    }

    return new XmlFileViewProvider(manager, file, physical, this);
  }

  private ParameterInfoHandler[] myHandlers;

  public @Nullable ParameterInfoHandler[] getParameterInfoHandlers() {
    if (myHandlers == null) {
      myHandlers = new ParameterInfoHandler[] {
        new XmlParameterInfoHandler()
      };
    }
    return myHandlers;
  }

}
