package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.getters.AllWordsGetter;
import com.intellij.psi.filters.getters.XmlAttributeValueGetter;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 18:55:15
 * To change this template use Options | File Templates.
 */
public class XmlCompletionData extends CompletionData {
  public XmlCompletionData() {
    declareFinalScope(XmlTag.class);
    declareFinalScope(XmlAttribute.class);
    declareFinalScope(XmlAttributeValue.class);

    {
      final CompletionVariant variant = new CompletionVariant(createTagCompletionFilter());
      variant.includeScopeClass(XmlTag.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      variant.setInsertHandler(createTagInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeCompletionFilter());
      variant.includeScopeClass(XmlAttribute.class);
      variant.addCompletionFilter(TrueFilter.INSTANCE);
      variant.setInsertHandler(new XmlAttributeInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(createAttributeValueCompletionFilter());
      variant.includeScopeClass(XmlAttributeValue.class);
      variant.addCompletion(getAttributeValueGetter());
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailType.NONE);
      variant.setInsertHandler(new XmlAttributeValueInsertHandler());
      registerVariant(variant);
    }

    final ElementFilter entityCompletionFilter = createXmlEntityCompletionFilter();

    {
      final CompletionVariant variant = new CompletionVariant(
          new AndFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS), new NotFilter(entityCompletionFilter)));
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new SimpleTagContentEnumerationValuesGetter(), TailType.NONE);

      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(entityCompletionFilter);
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new EntityRefGetter());
      variant.setInsertHandler(new EntityRefInsertHandler());
      registerVariant(variant);
    }
  }

  protected XmlTagInsertHandler createTagInsertHandler() {
    return new XmlTagInsertHandler();
  }

  protected ElementFilter createXmlEntityCompletionFilter() {
    return new AndFilter(new LeftNeighbour(new XmlTextFilter("&")), new OrFilter(new XmlTokenTypeFilter(XmlTokenType.XML_DATA_CHARACTERS),
                                                                                 new XmlTokenTypeFilter(
                                                                                     XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)));
  }

  protected XmlAttributeValueGetter getAttributeValueGetter() {
    return new XmlAttributeValueGetter();
  }

  protected ElementFilter createAttributeCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createAttributeValueCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  protected ElementFilter createTagCompletionFilter() {
    return TrueFilter.INSTANCE;
  }

  private static class XmlAttributeValueInsertHandler extends BasicInsertHandler {
    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);

      eatClosingQuote(context.getCompletionChar(), context.getEditor());

    }

  }

  public static int eatClosingQuote(char completionChar, final Editor editor) {
    final Document document = editor.getDocument();
    int tailOffset = editor.getCaretModel().getOffset();
    if (completionChar == '\"' || completionChar == '\'') {
      if (document.getTextLength() > tailOffset) {
        final char c = document.getCharsSequence().charAt(tailOffset);
        if (c == completionChar || completionChar == '\'') {
          editor.getCaretModel().moveToOffset(tailOffset + 1);
          return tailOffset + 1;
        }
      }
    }
    return tailOffset;
  }


  private static class XmlAttributeInsertHandler extends BasicInsertHandler {
    public XmlAttributeInsertHandler() {
    }

    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);

      final Editor editor = context.getEditor();

      final Document document = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document).getLanguage() == HTMLLanguage.INSTANCE &&
          HtmlUtil.isSingleHtmlAttribute((String)((MutableLookupElement)item).getObject())) {
        return;
      }

      final CharSequence chars = document.getCharsSequence();
      if (!CharArrayUtil.regionMatches(chars, caretOffset, "=\"") && !CharArrayUtil.regionMatches(chars, caretOffset, "='")) {
        if (caretOffset >= document.getTextLength() || "/> \n\t\r".indexOf(document.getCharsSequence().charAt(caretOffset)) < 0) {
          document.insertString(caretOffset, "=\"\" ");
        }
        else {
          document.insertString(caretOffset, "=\"\"");
        }
      }

      editor.getCaretModel().moveToOffset(caretOffset + 2);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }

  private static class SimpleTagContentEnumerationValuesGetter implements ContextGetter {
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
      if (tag != null) {
        final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent(tag);
        if (simpleContent != null) {
          final HashSet<String> variants = new HashSet<String>();
          XmlUtil.collectEnumerationValues(simpleContent, variants);
          if (variants.size() > 0) return variants.toArray(new Object[variants.size()]);
        }

        for (final PsiReference reference : tag.getReferences()) {
          if (!(reference instanceof TagNameReference)) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
          }
        }
      }

      return new AllWordsGetter().get(context, completionContext);
    }
  }

  protected static class EntityRefGetter implements ContextGetter {

    @Nullable
    private static Object getLookupItem(@Nullable final XmlEntityDecl decl) {
      if (decl == null) {
        return null;
      }

      final String name = decl.getName();
      if (name == null) {
        return null;
      }

      final XmlAttributeValue value = decl.getValueElement();
      final ASTNode node = value.getNode();
      if (node != null) {
        final ASTNode[] nodes = node.getChildren(TokenSet.create(XmlTokenType.XML_CHAR_ENTITY_REF));
        if (nodes.length == 1) {
          final String valueText = nodes[0].getText();
          final int i = valueText.indexOf('#');
          if (i > 0) {
            String s = valueText.substring(i + 1);
            if (s.endsWith(";")) {
              s = s.substring(0, s.length() - 1);
            }

            try {
              final int unicodeChar = Integer.valueOf(s).intValue();
              return LookupValueFactory.createLookupValueWithHint(name, null, new String(Character.toChars(unicodeChar)));
            }
            catch (NumberFormatException e) {
              return null;
            }
          }
        }
      }

      return null;
    }

    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      final XmlTag parentOfType = PsiTreeUtil.getParentOfType(context, XmlTag.class);
      if (parentOfType != null) {
        final List<Object> results = new ArrayList<Object>();
        final XmlFile containingFile = (XmlFile)parentOfType.getContainingFile();

        XmlFile descriptorFile = findDescriptorFile(parentOfType, containingFile);

        if (descriptorFile != null) {
          final boolean acceptSystemEntities = containingFile.getFileType() == StdFileTypes.XML;

          final PsiElementProcessor processor = new PsiElementProcessor() {
            public boolean execute(final PsiElement element) {
              if (element instanceof XmlEntityDecl) {
                final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
                if (xmlEntityDecl.isInternalReference() || acceptSystemEntities) {
                  final String name = xmlEntityDecl.getName();
                  final Object _item = getLookupItem(xmlEntityDecl);
                  results.add(_item == null ? name : _item);
                }
              }
              return true;
            }
          };

          XmlUtil.processXmlElements(descriptorFile, processor, true);

          return results.toArray(new Object[results.size()]);
        }
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  public static XmlFile findDescriptorFile(final XmlTag tag, final XmlFile containingFile) {
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    final XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    XmlFile descriptorFile = nsDescriptor != null
                             ? nsDescriptor.getDescriptorFile()
                             : containingFile.getDocument().getProlog().getDoctype() != null ? containingFile : null;
    if (nsDescriptor != null && (descriptorFile == null || descriptorFile.getName().equals(containingFile.getName() + ".dtd"))) {
      descriptorFile = containingFile;
    }
    return descriptorFile;
  }

  protected static class EntityRefInsertHandler extends BasicInsertHandler {
    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);

      final CaretModel caretModel = context.getEditor().getCaretModel();
      context.getEditor().getDocument().insertString(caretModel.getOffset(), ";");
      caretModel.moveToOffset(caretModel.getOffset() + 1);
      eatClosingQuote(context.getCompletionChar(), context.getEditor());
    }
  }
}
