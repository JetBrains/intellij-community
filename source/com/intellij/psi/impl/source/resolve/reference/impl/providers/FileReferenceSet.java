package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.javaee.web.WebModuleProperties;
import com.intellij.javaee.web.WebUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
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
  private final ReferenceType myType;
  private final PsiReferenceProvider myProvider;
  private boolean myCaseSensitive;
  private String myPathString;
  private final boolean myAllowEmptyFileReferenceAtEnd;

  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile,PsiElement>>
    DEFAULT_PATH_EVALUATOR_OPTION = new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile,PsiElement>>(
    PsiBundle.message("default.path.evaluator.option")
  );

  private final @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive){
    this(str, element, startInElement, type, provider, isCaseSensitive, true);
  }

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive,
                          boolean allowEmptyFileReferenceAtEnd){
    myType = type;
    myElement = element;
    myStartInElement = startInElement;
    myProvider = provider;
    myCaseSensitive = isCaseSensitive;
    myPathString = str.trim();
    myAllowEmptyFileReferenceAtEnd = allowEmptyFileReferenceAtEnd;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;

    reparse(str);
  }

  PsiElement getElement() {
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
    for(FileReference ref:myReferences) {
      ref.clearResolveCaches();
    }
  }

  int getStartInElement() {
    return myStartInElement;
  }

  PsiReferenceProvider getProvider() {
    return myProvider;
  }

  protected void reparse(String str){
    final List<FileReference> referencesList = new ArrayList<FileReference>();
    int currentSlash = -1;
    while(currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == ' ') currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;

    while(true){
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      if (subreferenceText.length() > 0 || myAllowEmptyFileReferenceAtEnd) { // ? check at end
        FileReference currentContextRef = new FileReference(this, new TextRange(myStartInElement + currentSlash + 1, myStartInElement + (
          nextSlash > 0
          ? nextSlash
          : str.length())), index++, subreferenceText);
        referencesList.add(currentContextRef);
      }
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    setReferences(referencesList.toArray(new FileReference[referencesList.size()]));
  }

  protected void setReferences(final FileReference[] references) {
    myReferences = references;
  }

  public FileReference getReference(int index){
    return myReferences[index];
  }

  public FileReference[] getAllReferences(){
    return myReferences;
  }

  ReferenceType getType(int index){
    if(index != myReferences.length - 1){
      return new ReferenceType(new int[] {myType.getPrimitives()[0], ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.DIRECTORY});
    }
    return myType;
  }

  protected boolean isSoft(){
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
      final Function<PsiFile,PsiElement> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);

      if (value != null) {
        final PsiElement result = value.fun(file);
        return result == null ?
           Collections.<PsiElement>emptyList() :
           Collections.singleton(result);
      }
    }

    final WebModuleProperties properties = WebUtil.getWebModuleProperties(file);

    PsiElement result = null;
    if (myPathString.startsWith(SEPARATOR_STRING)) {
      result = getAbsoluteTopLevelDirLocation(properties, project, file);
    }
    else {
      final PsiDirectory dir = file.getContainingDirectory();
      if (dir != null) {
        if (properties != null) {
          result = JspManager.getInstance(project).findWebDirectoryByFile(dir.getVirtualFile(), properties);
          if (result == null) result = dir;
        }
        else {
          result = dir;
        }
      }
    }

    return result == null ?
           Collections.<PsiElement>emptyList() :
           Collections.singleton(result);
  }

  public static PsiElement getAbsoluteTopLevelDirLocation(final WebModuleProperties properties,
                                                           final Project project,
                                                           final PsiFile file) {
    PsiElement result = null;

    if (properties != null) {
      result = JspManager.getInstance(project).findWebDirectoryElementByPath("/", properties);
    }
    else {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final VirtualFile contentRootForFile = ProjectRootManager.getInstance(project).getFileIndex()
          .getContentRootForFile(virtualFile);
        if (contentRootForFile != null) {
          result = PsiManager.getInstance(project).findDirectory(contentRootForFile);
        }
      }
    }
    return result;
  }

  protected PsiScopeProcessor createProcessor(final List result, ReferenceType type) throws ProcessorRegistry.IncompatibleReferenceTypeException {
    return ProcessorRegistry.getProcessorByType(type, result, null);
  }
}
