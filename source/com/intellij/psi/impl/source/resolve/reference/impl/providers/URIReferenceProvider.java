package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ManuallySetupExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Arrays;

/**
 * @by Maxim.Mossienko
 */
public class URIReferenceProvider implements PsiReferenceProvider {

  public static final ElementFilter ELEMENT_FILTER = new ElementFilter() {
    public boolean isAcceptable(Object element, PsiElement context) {
      final PsiElement parent = context.getParent();
      if (parent instanceof XmlAttribute) {
        final XmlAttribute attribute = ((XmlAttribute)parent);
        return attribute.isNamespaceDeclaration();
      }
      return false;
    }

    public boolean isClassAcceptable(Class hintClass) {
      return true;
    }
  };
  @NonNls private static final String HTTP = "http://";
  @NonNls private static final String URN = "urn:";
  @NonNls private static final String FILE = "file:";
  @NonNls private static final String CLASSPATH = "classpath:/";

  public static class DependentNSReference extends BasicAttributeValueReference implements QuickFixProvider {
    private final URLReference myReference;

    public DependentNSReference(final PsiElement element, TextRange range, URLReference ref) {
      super(element, range);
      myReference = ref;
    }

    public PsiFile resolveResource() {
      final String canonicalText = getCanonicalText();
      return ExternalResourceManager.getInstance().getResourceLocation(canonicalText, myElement.getContainingFile(), null);
    }

    @Nullable
    public PsiElement resolve() {
      final PsiFile file = resolveResource();
      if (file != null) return file;
      return myReference.resolve();
    }

    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return false;
    }

    public void registerQuickfix(HighlightInfo info, PsiReference reference) {
      QuickFixAction.registerQuickFixAction(info, new FetchExtResourceAction());
      QuickFixAction.registerQuickFixAction(info, new ManuallySetupExtResourceAction());
      QuickFixAction.registerQuickFixAction(info, new IgnoreExtResourceAction());
    }
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    final String text = element.getText();
    String s = StringUtil.stripQuotesAroundValue(text);
    final PsiElement parent = element.getParent();

    if (parent instanceof XmlAttribute && "xsi:schemaLocation".equals(((XmlAttribute)parent).getName())) {
      final List<PsiReference> refs = new ArrayList<PsiReference>(2);
      final StringTokenizer tokenizer = new StringTokenizer(s);

      while(tokenizer.hasMoreElements()) {
        final String namespace = tokenizer.nextToken();
        int offset = text.indexOf(namespace);
        final URLReference urlReference = new URLReference(element, new TextRange(offset, offset + namespace.length()), true);
        refs.add(urlReference);
        if (!tokenizer.hasMoreElements()) break;
        String url = tokenizer.nextToken();

        offset = text.indexOf(url);
        if (isUrlText(url)) refs.add(new DependentNSReference(element, new TextRange(offset,offset + url.length()), urlReference));
        else {
          refs.addAll(Arrays.asList(new FileReferenceSet(url, element, offset, this, false).getAllReferences()));
        }
      }

      return refs.toArray(new PsiReference[refs.size()]);
    }

    if (isUrlText(s) || (parent instanceof XmlAttribute && ((XmlAttribute)parent).isNamespaceDeclaration())) {
      if (!s.startsWith(JspManager.TAG_DIR_NS_PREFIX)) {
        final boolean namespaceSoftRef = parent instanceof XmlAttribute &&
          "namespace".equals(((XmlAttribute)parent).getName()) &&
          ((XmlAttribute)parent).getParent().getAttributeValue("schemaLocation") != null;

        return getUrlReference(element, namespaceSoftRef);
      }
    }

    s = s.substring(getPrefixLength(s));
    return new FileReferenceSet(s,element,text.indexOf(s), ReferenceType.FILE_TYPE, this,true).getAllReferences();
  }

  public static int getPrefixLength(@NotNull final String s) {
    if (s.startsWith(JspManager.TAG_DIR_NS_PREFIX)) return JspManager.TAG_DIR_NS_PREFIX.length();
    if (s.startsWith(FILE)) return FILE.length();
    if (s.startsWith(CLASSPATH)) return CLASSPATH.length();
    return 0;
  }

  static boolean isUrlText(final String s) {
    return s.startsWith(HTTP) || s.startsWith(URN);
  }

  private static URLReference[] getUrlReference(final PsiElement element, boolean soft) {
    return new URLReference[] { new URLReference(element, null, soft)};
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }
}
