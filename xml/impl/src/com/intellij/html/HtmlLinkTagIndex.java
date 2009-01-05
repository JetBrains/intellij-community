package com.intellij.html;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.HtmlHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XHtmlHighlightingLexer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 */
public class HtmlLinkTagIndex implements FileBasedIndexExtension<Integer, List<HtmlLinkTagIndex.LinkInfo>> {
  public static final ID<Integer, List<LinkInfo>> INDEX_ID = ID.create("HtmlLinkTagIndex");
  private final EnumeratorIntegerDescriptor myKeyDescriptor = new EnumeratorIntegerDescriptor();

  @NonNls private static final String LINK = "link";
  @NonNls private static final String HREF_ATTR = "href";
  @NonNls private static final String BODY_TAG = "body";

  private FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      final FileType fileType = file.getFileType();
      if (!(fileType instanceof LanguageFileType)) {
        return false;
      }

      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      final Language language = languageFileType.getLanguage();

      return language instanceof XMLLanguage && language != XMLLanguage.INSTANCE;
    }
  };
  private DataExternalizer<List<LinkInfo>> myValueExternalizer = new DataExternalizer<List<LinkInfo>>() {
    public void save(DataOutput out, List<LinkInfo> value) throws IOException {
      out.writeInt(value.size());
      for (final LinkInfo linkInfo : value) {
        out.writeInt(linkInfo.offset);
        out.writeBoolean(linkInfo.scripted);
        if (!linkInfo.scripted) {
          out.writeUTF(linkInfo.value);
        }
      }
    }

    public List<LinkInfo> read(DataInput in) throws IOException {
      final int size = in.readInt();
      final List<LinkInfo> list = new ArrayList<LinkInfo>(size);
      for (int i = 0; i < size; i++) {
        final int offset = in.readInt();
        final boolean scripted = in.readBoolean();
        list.add(new LinkInfo(offset, scripted, scripted ? null : in.readUTF()));
      }

      return list;
    }
  };

  public ID<Integer, List<LinkInfo>> getName() {
    return INDEX_ID;
  }

  public interface LinkReferenceResult {
    @Nullable
    PsiFile getReferencedFile();

    boolean isScriptedReference();
  }

  @NotNull
  public static List<LinkReferenceResult> getReferencedFiles(@NotNull final VirtualFile file, @NotNull final Project project) {
    final List<LinkReferenceResult> result = new ArrayList<LinkReferenceResult>();
    FileBasedIndex.getInstance()
      .processValues(INDEX_ID, FileBasedIndex.getFileId(file), null, new FileBasedIndex.ValueProcessor<List<LinkInfo>>() {
        public void process(final VirtualFile file, final List<LinkInfo> value) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            for (LinkInfo linkInfo : value) {
              if (linkInfo.scripted) {
                result.add(new LinkReferenceResult() {
                  public PsiFile getReferencedFile() {
                    return null;
                  }

                  public boolean isScriptedReference() {
                    return true;
                  }
                });
              }
              else {
                final LeafElement newValueElement = Factory
                  .createSingleLeafElement(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, "\"" + linkInfo.value + "\"", 0,
                                           linkInfo.value.length() + 2, null, psiManager, psiFile);
                final PsiElement element = newValueElement.getPsi();
                final FileReferenceSet set =
                  new FileReferenceSet(StringUtil.stripQuotesAroundValue(element.getText()), element, 1, null, true);

                final FileReference lastReference = set.getLastReference();
                if (lastReference != null) {
                  final PsiFileSystemItem item = lastReference.resolve();
                  if (item != null) {
                    result.add(new LinkReferenceResult() {
                      public PsiFile getReferencedFile() {
                        return (PsiFile) item;
                      }

                      public boolean isScriptedReference() {
                        return false;
                      }
                    });
                  }
                }
              }
            }
          }
        }
      }, VirtualFileFilter.ALL);

    return result;
  }

  public DataIndexer<Integer, List<LinkInfo>, FileContent> getIndexer() {
    return new DataIndexer<Integer, List<LinkInfo>, FileContent>() {
      @NotNull
      public Map<Integer, List<LinkInfo>> map(final FileContent inputData) {
        final VirtualFile file = inputData.getFile();
        final int id = FileBasedIndex.getFileId(file);
        final Language language = ((LanguageFileType)file.getFileType()).getLanguage();

        final Map<Integer, List<LinkInfo>> result = new THashMap<Integer, List<LinkInfo>>();

        if (HTMLLanguage.INSTANCE == language || XHTMLLanguage.INSTANCE == language) {
          final Lexer original = HTMLLanguage.INSTANCE == language ? new HtmlHighlightingLexer() : new XHtmlHighlightingLexer();
          final Lexer lexer = new FilterLexer(original, new FilterLexer.Filter() {
            public boolean reject(final IElementType type) {
              return XmlElementType.XML_WHITE_SPACE == type;
            }
          });

          final CharSequence data = inputData.getContentAsText();
          lexer.start(data, 0, data.length(), 0);

          IElementType tokenType = lexer.getTokenType();
          boolean linkTag = false;
          while (tokenType != null) {
            if (XmlElementType.XML_TAG_NAME == tokenType) {
              final String tagName = data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
              linkTag = LINK.equalsIgnoreCase(tagName);

              if (BODY_TAG.equalsIgnoreCase(tagName)) {
                break; // there are no LINK tags under the body
              }
            }

            if (linkTag && XmlElementType.XML_NAME == tokenType) {
              final String attrName = data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
              if (HREF_ATTR.equalsIgnoreCase(attrName)) {
                lexer.advance();
                tokenType = lexer.getTokenType();
                if (XmlElementType.XML_EQ == tokenType) {
                  lexer.advance();
                  tokenType = lexer.getTokenType();

                  if (tokenType == XmlElementType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
                    lexer.advance();
                    tokenType = lexer.getTokenType();

                    final int identOffset = lexer.getTokenStart();
                    if (XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN == tokenType) {
                      final String value = data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
                      addResult(result, id, identOffset, value, false);
                    }
                  }
                }
              }
            }

            lexer.advance();
            tokenType = lexer.getTokenType();
          }
        }
        else {
          Project project = ProjectManager.getInstance().getDefaultProject(); // TODO
          final LightVirtualFile lightVirtualFile = new LightVirtualFile(inputData.getFileName(), inputData.getContentAsText());
          final FileViewProviderFactory viewProviderFactory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
          if (viewProviderFactory != null) {
            final FileViewProvider viewProvider =
              viewProviderFactory.createFileViewProvider(lightVirtualFile, language, PsiManager.getInstance(project), false);

            final PsiFile psiFile;
            if (viewProvider instanceof TemplateLanguageFileViewProvider) {
              final Language dataLanguage = ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage();
              psiFile = viewProvider.getPsi(dataLanguage);
            }
            else {
              psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
            }

            if (psiFile != null) {
              final XmlRecursiveElementVisitor visitor = new XmlRecursiveElementVisitor() {
                @Override
                public void visitXmlTag(XmlTag tag) {
                  if (LINK.equalsIgnoreCase(tag.getLocalName())) {
                    final XmlAttribute href = tag.getAttribute(HREF_ATTR);
                    if (href != null) {
                      final XmlAttributeValue value = href.getValueElement();
                      if (value != null) {
                        if (PsiTreeUtil.getChildOfType(value, OuterLanguageElement.class) == null) {
                          final String textValue = value.getValue();
                          if (textValue != null && textValue.length() > 0) {
                            addResult(result, id, value.getTextOffset(), textValue, false);
                          }
                        }
                        else {
                          addResult(result, id, value.getTextOffset(), null, true);
                        }


                      }
                    }
                  }

                  super.visitXmlTag(tag);
                }
              };

              ChameleonTransforming.transformChildrenNoLock(psiFile.getNode(), true);
              psiFile.accept(visitor);
            }
          }
        }

        return result;
      }
    };
  }

  private static void addResult(final Map<Integer, List<LinkInfo>> result,
                                final int id,
                                final int offset,
                                final String textValue,
                                final boolean scripted) {
    List<LinkInfo> infos = result.get(id);
    if (infos == null) {
      infos = new ArrayList<LinkInfo>();
      result.put(id, infos);
    }

    infos.add(new LinkInfo(offset, scripted, textValue));
  }

  public KeyDescriptor<Integer> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public DataExternalizer<List<LinkInfo>> getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 1;
  }

  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }


  public static class LinkInfo {
    public int offset;
    public String value;
    public boolean scripted;

    public LinkInfo(final int textOffset, final boolean scriptedRef, @Nullable final String textValue) {
      offset = textOffset;
      value = textValue;
      scripted = scriptedRef;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final LinkInfo linkInfo = (LinkInfo)o;

      if (offset != linkInfo.offset) return false;
      if (scripted != linkInfo.scripted) return false;
      if (value != null ? !value.equals(linkInfo.value) : linkInfo.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = offset;
      result = 31 * result + (value != null ? value.hashCode() : 0);
      result = 31 * result + (scripted ? 1 : 0);
      return result;
    }
  }

}
