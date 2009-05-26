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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author spleaner
 */
public class HtmlLinkTagIndex extends SingleEntryFileBasedIndexExtension<HtmlLinkTagIndex.InfoHolder<HtmlLinkTagIndex.LinkInfo>> {
  public static final ID<Integer, InfoHolder<LinkInfo>> INDEX_ID = ID.create("HtmlLinkTagIndex");

  @NonNls private static final String LINK = "link";
  @NonNls private static final String HREF_ATTR = "href";
  @NonNls private static final String MEDIA_ATTR = "media";
  @NonNls private static final String REL_ATTR = "rel";
  @NonNls private static final String TITLE_ATTR = "title";
  @NonNls private static final String TYPE_ATTR = "type";

  private final FileBasedIndex.InputFilter myInputFilter = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      if (!(file.getFileSystem() instanceof LocalFileSystem)) {
        return false;
      }

      final FileType fileType = file.getFileType();
      if (!(fileType instanceof LanguageFileType)) {
        return false;
      }

      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      final Language language = languageFileType.getLanguage();

      return language instanceof TemplateLanguage || (language instanceof XMLLanguage && language != XMLLanguage.INSTANCE);
    }
  };
  private final DataExternalizer<InfoHolder<LinkInfo>> myValueExternalizer = new DataExternalizer<InfoHolder<LinkInfo>>() {
    public void save(DataOutput out, InfoHolder<LinkInfo> value) throws IOException {
      out.writeInt(value.myValues.length);
      for (final LinkInfo linkInfo : value.myValues) {
        out.writeInt(linkInfo.offset);
        out.writeBoolean(linkInfo.scripted);

        if (!linkInfo.scripted) {
          writeString(linkInfo.value, out);
        }

        writeString(linkInfo.media, out);
        writeString(linkInfo.type, out);
        writeString(linkInfo.rel, out);
        writeString(linkInfo.title, out);
      }
    }

    private void writeString(String s, DataOutput out) throws IOException {
      out.writeBoolean(s != null);
      if (s != null) {
        out.writeUTF(s);
      }
    }

    public InfoHolder<LinkInfo> read(DataInput in) throws IOException {
      final int size = in.readInt();
      final List<LinkInfo> list = new ArrayList<LinkInfo>(size);
      for (int i = 0; i < size; i++) {
        final int offset = in.readInt();
        final boolean scripted = in.readBoolean();

        final String href = !scripted ? (in.readBoolean() ? in.readUTF() : null) : null;
        final String media = in.readBoolean() ? in.readUTF() : null;
        final String type = in.readBoolean() ? in.readUTF() : null;
        final String rel = in.readBoolean() ? in.readUTF() : null;
        final String title = in.readBoolean() ? in.readUTF() : null;

        list.add(new LinkInfo(offset, scripted, href, media, type, rel, title));
      }

      return new InfoHolder<LinkInfo>(list.toArray(new LinkInfo[list.size()]));
    }
  };

  public ID<Integer, InfoHolder<LinkInfo>> getName() {
    return INDEX_ID;
  }

  public interface LinkReferenceResult {
    @Nullable
    PsiFile getReferencedFile();

    @Nullable
    PsiFile resolve();

    boolean isScriptedReference();

    @Nullable
    String getMediaValue();

    @Nullable
    String getTypeValue();

    @Nullable
    String getHrefValue();

    @Nullable
    String getRelValue();

    @Nullable
    String getTitleValue();
  }

  @NotNull
  public static List<LinkReferenceResult> getReferencedFiles(@NotNull final VirtualFile _file, @NotNull final Project project) {
    final List<LinkReferenceResult> result = new ArrayList<LinkReferenceResult>();
    if (!(_file.getFileSystem() instanceof LocalFileSystem)) {
      return result;
    }

    FileBasedIndex.getInstance()
      .processValues(INDEX_ID, FileBasedIndex.getFileId(_file), null, new FileBasedIndex.ValueProcessor<InfoHolder<LinkInfo>>() {
        public boolean process(final VirtualFile file, final InfoHolder<LinkInfo> value) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            for (final LinkInfo linkInfo : value.myValues) {
              if (linkInfo.value != null || linkInfo.scripted) {
                final PsiFileSystemItem[] item = new PsiFileSystemItem[]{null};
                if (linkInfo.value != null) {
                  final LeafElement newValueElement = Factory
                    .createSingleLeafElement(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN, "\"" + linkInfo.value + "\"", 0,
                                             linkInfo.value.length() + 2, null, psiManager, psiFile);
                  final PsiElement element = newValueElement.getPsi();
                  final FileReferenceSet set =
                    new FileReferenceSet(StringUtil.stripQuotesAroundValue(element.getText()), element, 1, null, true);

                  final FileReference lastReference = set.getLastReference();

                  if (lastReference != null) {
                    final PsiFileSystemItem resolved = lastReference.resolve();
                    if (resolved instanceof PsiFile) {
                      item[0] = resolved;
                    }
                  }
                }

                result.add(new MyLinkReferenceResult(item, linkInfo, psiFile));
              }
            }
          }
          return true;
        }
      }, GlobalSearchScope.allScope(project));

    return result;
  }

  public SingleEntryIndexer<InfoHolder<LinkInfo>> getIndexer() {
    return new SingleEntryIndexer<InfoHolder<LinkInfo>>(false) {
      protected InfoHolder<LinkInfo> computeValue(@NotNull FileContent inputData) {
        final Language language = ((LanguageFileType)inputData.getFileType()).getLanguage();

        final List<LinkInfo> result = new ArrayList<LinkInfo>();

        if (HTMLLanguage.INSTANCE == language || XHTMLLanguage.INSTANCE == language) {
          mapHtml(inputData, language, result);
        }
        else {
          mapJsp(inputData, language, result);
        }

        return new InfoHolder<LinkInfo>(result.toArray(new LinkInfo[result.size()]));
      }
    };
  }

  private static void mapJsp(FileContent inputData, Language language, final List<LinkInfo> result) {
    Project project = ProjectManager.getInstance().getDefaultProject(); // TODO
    final LightVirtualFile lightVirtualFile = new LightVirtualFile(inputData.getFileName(), inputData.getContentAsText());
    PsiFile psiFile = null;

    final FileViewProviderFactory viewProviderFactory = LanguageFileViewProviders.INSTANCE.forLanguage(language);
    if (viewProviderFactory == null) {
      return;
    }

    final FileViewProvider viewProvider =
      viewProviderFactory.createFileViewProvider(lightVirtualFile, language, PsiManager.getInstance(project), false);

    if (viewProvider instanceof TemplateLanguageFileViewProvider) {
      final Language dataLanguage = ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage();
      if (dataLanguage == HTMLLanguage.INSTANCE || dataLanguage == XHTMLLanguage.INSTANCE) {
        psiFile = viewProvider.getPsi(dataLanguage);
      }
    }
    else {
      psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
    }

    if (psiFile != null) {
      final XmlRecursiveElementVisitor visitor = new XmlRecursiveElementVisitor() {
        @Override
        public void visitXmlTag(XmlTag tag) {
          if (LINK.equalsIgnoreCase(tag.getLocalName())) {

            final String href = getAttributeValue(tag, HREF_ATTR);
            final String media = getAttributeValue(tag, MEDIA_ATTR);
            final String type = getAttributeValue(tag, TYPE_ATTR);
            final String rel = getAttributeValue(tag, REL_ATTR);
            final String title = getAttributeValue(tag, TITLE_ATTR);

            addResult(result, tag.getTextOffset(), href, media, type, rel, title, isHrefScripted(tag));
          }

          super.visitXmlTag(tag);
        }
      };

      psiFile.accept(visitor);
    }
  }

  private static void mapHtml(FileContent inputData, Language language, List<LinkInfo> result) {
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
        //if (BODY_TAG.equalsIgnoreCase(tagName)) {
        //  break; // there are no LINK tags under the body
        //}
      }

      if (linkTag && XmlElementType.XML_NAME == tokenType) {
        int linkTagOffset = lexer.getTokenStart();
        String href = null;
        String type = null;
        String media = null;
        String rel = null;
        String title = null;

        while (true) {
          if (tokenType == null ||
              tokenType == XmlTokenType.XML_END_TAG_START ||
              tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
              tokenType == XmlTokenType.XML_START_TAG_START) {
            break;
          }

          if (XmlElementType.XML_NAME == tokenType) {
            final String attrName = data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
            if (HREF_ATTR.equalsIgnoreCase(attrName)) {
              href = parseAttributeValue(lexer, data);
            }
            else if (MEDIA_ATTR.equalsIgnoreCase(attrName)) {
              media = parseAttributeValue(lexer, data);
            }
            else if (TYPE_ATTR.equalsIgnoreCase(attrName)) {
              type = parseAttributeValue(lexer, data);
            }
            else if (REL_ATTR.equalsIgnoreCase(attrName)) {
              rel = parseAttributeValue(lexer, data);
            }
            else if (TITLE_ATTR.equalsIgnoreCase(attrName)) {
              title = parseAttributeValue(lexer, data);
            }
          }

          lexer.advance();
          tokenType = lexer.getTokenType();
        }

        addResult(result, linkTagOffset, href, media, type, rel, title, false);
      }

      lexer.advance();
      tokenType = lexer.getTokenType();
    }
  }

  private static boolean isHrefScripted(final XmlTag tag) {
    final XmlAttribute attribute = tag.getAttribute(HREF_ATTR);
    if (attribute != null) {
      final XmlAttributeValue value = attribute.getValueElement();
      if (value != null) {
        if (PsiTreeUtil.getChildOfType(value, OuterLanguageElement.class) != null) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static String getAttributeValue(final XmlTag tag, final String attrName) {
    final XmlAttribute attribute = tag.getAttribute(attrName);
    if (attribute != null) {
      final XmlAttributeValue value = attribute.getValueElement();
      if (value != null) {
        if (PsiTreeUtil.getChildOfType(value, OuterLanguageElement.class) == null) {
          return value.getValue();
        }
      }
    }

    return null;
  }

  @Nullable
  private static String parseAttributeValue(final Lexer lexer, CharSequence data) {
    lexer.advance();
    IElementType tokenType = lexer.getTokenType();
    if (XmlElementType.XML_EQ == tokenType) {
      lexer.advance();
      tokenType = lexer.getTokenType();

      if (tokenType == XmlElementType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
        lexer.advance();
        tokenType = lexer.getTokenType();

        if (XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN == tokenType) {
          return data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
        }
      }
      else if (tokenType != XmlTokenType.XML_TAG_END && tokenType != XmlTokenType.XML_EMPTY_ELEMENT_END) {
        return data.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
      }
    }

    return null;
  }

  private static void addResult(final List<LinkInfo> result,
                                final int offset,
                                final String hrefValue,
                                final String mediaValue,
                                final String typeValue,
                                final String relValue,
                                final String titleValue,
                                final boolean scripted) {
    result.add(new LinkInfo(offset, scripted, hrefValue, mediaValue, typeValue, relValue, titleValue));
  }

  public DataExternalizer<InfoHolder<LinkInfo>> getValueExternalizer() {
    return myValueExternalizer;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public int getVersion() {
    return 4;
  }

  public static class LinkInfo {
    public int offset;
    public String value;
    public String media;
    public String type;
    public String rel;
    public String title;
    public boolean scripted;

    public LinkInfo(final int textOffset,
                    final boolean scriptedRef,
                    @Nullable final String hrefValue,
                    @Nullable final String mediaValue,
                    @Nullable final String typeValue,
                    @Nullable final String relValue,
                    @Nullable final String titleValue) {
      offset = textOffset;
      scripted = scriptedRef;
      value = hrefValue;
      media = mediaValue;
      type = typeValue;
      rel = relValue;
      title = titleValue;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final LinkInfo linkInfo = (LinkInfo)o;

      if (offset != linkInfo.offset) return false;
      if (scripted != linkInfo.scripted) return false;
      if (media != null ? !media.equals(linkInfo.media) : linkInfo.media != null) return false;
      if (rel != null ? !rel.equals(linkInfo.rel) : linkInfo.rel != null) return false;
      if (title != null ? !title.equals(linkInfo.title) : linkInfo.title != null) return false;
      if (type != null ? !type.equals(linkInfo.type) : linkInfo.type != null) return false;
      if (value != null ? !value.equals(linkInfo.value) : linkInfo.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = offset;
      result = 31 * result + (value != null ? value.hashCode() : 0);
      result = 31 * result + (media != null ? media.hashCode() : 0);
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + (rel != null ? rel.hashCode() : 0);
      result = 31 * result + (title != null ? title.hashCode() : 0);
      result = 31 * result + (scripted ? 1 : 0);
      return result;
    }
  }

  private static class MyLinkReferenceResult implements LinkReferenceResult {
    private final PsiFileSystemItem[] myItem;
    private final LinkInfo myLinkInfo;
    private final PsiFile myPsiFile;

    public MyLinkReferenceResult(final PsiFileSystemItem[] item, final LinkInfo linkInfo, final PsiFile psiFile) {
      myItem = item;
      myLinkInfo = linkInfo;
      myPsiFile = psiFile;
    }

    public PsiFile getReferencedFile() {
      return (PsiFile)myItem[0];
    }

    public PsiFile resolve() {
      final PsiFile referencedFile = getReferencedFile();
      if (referencedFile != null) {
        return referencedFile;
      }

      if (myPsiFile != null) {
        final PsiElement psiElement = myPsiFile.findElementAt(myLinkInfo.offset);
        if (psiElement != null) {
          final PsiElement parent = psiElement.getParent();
          if (parent instanceof XmlTag) {
            final XmlAttribute attribute = ((XmlTag)parent).getAttribute(HREF_ATTR);
            if (attribute != null) {
              final XmlAttributeValue value = attribute.getValueElement();
              if (value != null) {
                final PsiReference[] references = value.getReferences();
                for (PsiReference reference : references) {
                  final PsiElement element = reference.resolve();
                  if (element instanceof PsiFile) {
                    return (PsiFile)element;
                  }
                }
              }
            }
          }
        }
      }

      return null;
    }

    public boolean isScriptedReference() {
      return myLinkInfo.scripted;
    }

    public String getMediaValue() {
      return myLinkInfo.media;
    }

    public String getTypeValue() {
      return myLinkInfo.type;
    }

    public String getHrefValue() {
      return myLinkInfo.value;
    }

    public String getRelValue() {
      return myLinkInfo.rel;
    }

    public String getTitleValue() {
      return myLinkInfo.title;
    }
  }

  static class InfoHolder<T> {
    public T[] myValues;

    InfoHolder(final T[] values) {
      myValues = values;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final InfoHolder that = (InfoHolder)o;

      if (!Arrays.equals(myValues, that.myValues)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myValues != null ? Arrays.hashCode(myValues) : 0;
    }
  }
}
