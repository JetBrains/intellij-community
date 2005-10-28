package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.j2ee.module.view.web.WebUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 16, 2005
 * Time: 9:43:15 PM
 * To change this template use File | Settings | File Templates.
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

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          ReferenceType type,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive){
    myType = type;
    myElement = element;
    myStartInElement = startInElement;
    myProvider = provider;
    myCaseSensitive = isCaseSensitive;
    myPathString = str.trim();

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
    FileReference currentContextRef;

    while(true){
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      currentContextRef = new FileReference(this, new TextRange(myStartInElement + currentSlash + 1,
                                                                myStartInElement + (nextSlash > 0 ? nextSlash : str.length())),
                                            index++, subreferenceText);
      referencesList.add(currentContextRef);
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
    LOG.assertTrue(file != null, "Invalid element: " + element);

    if (!file.isPhysical()) file = file.getOriginalFile();
    if (file == null) return Collections.emptyList();
    final WebModuleProperties properties = (WebModuleProperties)WebUtil.getWebModuleProperties(file);

    PsiElement result = null;
    if (myPathString.startsWith(SEPARATOR_STRING)) {
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

  protected PsiScopeProcessor createProcessor(final List result, ReferenceType type) throws ProcessorRegistry.IncompatibleReferenceTypeException {
    return ProcessorRegistry.getInstance().getProcessorByType(type, result, null);
  }
}
