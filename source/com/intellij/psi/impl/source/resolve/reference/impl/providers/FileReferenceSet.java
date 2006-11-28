package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.jsp.JspContextManager;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet");
  private static final char SEPARATOR = '/';
  private static final String SEPARATOR_STRING = "/";

  private FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  @NotNull private final ReferenceType myType;
  private final PsiReferenceProvider myProvider;
  private boolean myCaseSensitive;
  private String myPathString;

  public boolean isEndingSlashNotAllowed() {
    return myEndingSlashNotAllowed;
  }

  private final boolean myEndingSlashNotAllowed;
  private final boolean myUseIncludingJspFileAsContext;

  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, PsiElement>> DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, PsiElement>>(PsiBundle.message("default.path.evaluator.option"));

  private @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;

  @Nullable
  public static FileReferenceSet createSet(PsiElement element, final boolean soft, boolean endingSlashNotAllowed) {

    String text;
    int offset;

    if (element instanceof XmlAttributeValue) {
      text = ((XmlAttributeValue)element).getValue();
      String s = element.getText();
      offset = StringUtil.startsWithChar(s, '"') || StringUtil.startsWithChar(s, '\'') ? 1 : 0;
    }
    else if (element instanceof XmlTag) {
      final XmlTag tag = ((XmlTag)element);
      final XmlTagValue value = tag.getValue();
      final String s = value.getText();
      text = s.trim();
      offset = value.getTextRange().getStartOffset() + s.indexOf(text) - element.getTextOffset();
    }
    else {
      return null;
    }
    if (text == null) {
      return null;
    }

    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      text = helper.trimUrl(text);
    }

    return new FileReferenceSet(text, element, offset, ReferenceType.FILE_TYPE, null, true, endingSlashNotAllowed, true) {
      protected boolean isSoft() {
        return soft;
      }
    };
  }


  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, ReferenceType.FILE_TYPE, provider, isCaseSensitive, true, false);
  }

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          @NotNull ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, type, provider, isCaseSensitive, true, false);
  }

  public FileReferenceSet(@NotNull String str,
                          PsiElement element,
                          int startInElement,
                          @NotNull ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive,
                          boolean endingSlashNotAllowed,
                          boolean useIncludingJspFileAsContext) {
    myType = type;
    myElement = element;
    myStartInElement = startInElement;
    myProvider = provider;
    myCaseSensitive = isCaseSensitive;
    myPathString = str.trim();
    myEndingSlashNotAllowed = endingSlashNotAllowed;
    myUseIncludingJspFileAsContext = useIncludingJspFileAsContext;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;

    reparse(str);
  }

  public PsiElement getElement() {
    return myElement;
  }

  void setElement(final PsiElement element) {
    myElement = element;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setCaseSensitive(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    for (FileReference ref : myReferences) {
      ref.clearResolveCaches();
    }
  }

  public int getStartInElement() {
    return myStartInElement;
  }

  PsiReferenceProvider getProvider() {
    return myProvider;
  }

  protected FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new FileReference(this, range, index, text);
  }

  protected void reparse(String str) {
    final List<FileReference> referencesList = new ArrayList<FileReference>();
    // skip white space
    int currentSlash = -1;
    while (currentSlash + 1 < str.length() && Character.isWhitespace(str.charAt(currentSlash + 1))) currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;

    if (str.equals(SEPARATOR_STRING)) {
      final FileReference fileReference =
        createFileReference(new TextRange(myStartInElement, myStartInElement + 1), index++, SEPARATOR_STRING);
      referencesList.add(fileReference);
    }

    while (true) {
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      final FileReference ref = createFileReference(
        new TextRange(myStartInElement + currentSlash + 1, myStartInElement + (nextSlash > 0 ? nextSlash : str.length())),
        index++,
        subreferenceText);
      referencesList.add(ref);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    setReferences(referencesList.toArray(new FileReference[referencesList.size()]));
  }

  private void setReferences(final FileReference[] references) {
    myReferences = references;
  }

  public FileReference getReference(int index) {
    return myReferences[index];
  }

  @NotNull
  public FileReference[] getAllReferences() {
    return myReferences;
  }

  @NotNull final String getTypeName() {
    return ReferenceType.getUnresolvedMessage(myType.getPrimitives()[0]);
  }

  protected boolean isSoft() {
    return false;
  }

  @NotNull
  public Collection<PsiElement> getDefaultContexts(PsiElement element) {
    Project project = element.getProject();
    PsiFile file = element.getContainingFile();
    if (file == null) {
      LOG.assertTrue(false, "Invalid element: " + element);
    }

    if (!file.isPhysical()) file = file.getOriginalFile();
    if (file == null) return Collections.emptyList();
    if (myOptions != null) {
      final Function<PsiFile, PsiElement> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);

      if (value != null) {
        final PsiElement result = value.fun(file);
        return result == null ? Collections.<PsiElement>emptyList() : Collections.singleton(result);
      }
    }


    PsiElement result = null;
    if (isAbsolutePathReference()) {
      result = getAbsoluteTopLevelDirLocation(file);
    }
    else {
      if (myUseIncludingJspFileAsContext) {
        JspFile jspFile = PsiUtil.getJspFile(file);
        if (jspFile != null) {
          final JspContextManager manager = JspContextManager.getInstance(project);
          JspFile contextFile = manager.getContextFile(jspFile);
          Set<JspFile> visited = new HashSet<JspFile>();
          while (contextFile != null && !visited.contains(jspFile)) {
            visited.add(jspFile);
            jspFile = contextFile;
            contextFile = manager.getContextFile(jspFile);
          }
          file = jspFile;
        }
      }

      final List<FileReferenceHelper> helpers = FileReferenceHelperRegistrar.getHelpers();
      for (final FileReferenceHelper helper : helpers) {
        final PsiFileSystemItem item = helper.getContainingDirectory(file);
        if (item != null) {
          result = item;
          break;
        }
      }
    }

    return result == null ? Collections.<PsiElement>emptyList() : Collections.singleton(result);
  }

  public String getPathString() {
    return myPathString;
  }

  public boolean isAbsolutePathReference() {
    return myPathString.startsWith(SEPARATOR_STRING);
  }

  @Nullable
  public static PsiElement getAbsoluteTopLevelDirLocation(final PsiFile file) {
    final List<FileReferenceHelper> helpers = FileReferenceHelperRegistrar.getHelpers();
    for (final FileReferenceHelper helper : helpers) {
      final PsiElement element = helper.getAbsoluteTopLevelDirLocation(file);
      if (element != null) {
        return element;
      }
    }

    return null;
  }

  protected PsiScopeProcessor createProcessor(final List result, List<Class> allowedClasses, List<PsiConflictResolver> resolvers)
    throws ProcessorRegistry.IncompatibleReferenceTypeException {
    return ProcessorRegistry.getProcessorByType(myType, result, null, allowedClasses, resolvers);
  }

  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizableReferenceProvider.CustomizationKey, Object>(5);
    }
    myOptions.put(key, value);
  }
}
