package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.j2ee.module.view.web.WebUtil;
import com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.jsp.JspUtil;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author cdr
 */
public class FileReference implements PsiPolyVariantReference, QuickFixProvider {
  public static final FileReference[] EMPTY = new FileReference[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference");

  private final int myIndex;
  private TextRange myRange;
  private final String myText;
  private final FileReferenceSet myFileReferenceSet;

  public FileReference(final FileReferenceSet fileReferenceSet, TextRange range, int index, String text){
    myFileReferenceSet = fileReferenceSet;
    myIndex = index;
    myRange = range;
    myText = text;
  }

  public String getUnresolvedMessage(){
    return getType().getUnresolvedMessage();
  }

  private Collection<PsiElement> getContexts(){
    final FileReference contextRef = getContextReference();
    if (contextRef == null) {
      return myFileReferenceSet.getDefaultContexts(myFileReferenceSet.getElement());
    }
    else {
      ResolveResult[] resolveResults = contextRef.multiResolve(false);
      ArrayList<PsiElement> result = new ArrayList<PsiElement>();
      for (ResolveResult resolveResult : resolveResults) {
        result.add(resolveResult.getElement());
      }
      return result;
    }
  }

  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final Collection<PsiElement> contexts = getContexts();
    Collection<ResolveResult> result = new ArrayList<ResolveResult>();
    for (PsiElement context : contexts) {
      PsiElement resolved = null;
      if (context instanceof WebDirectoryElement) {
        if (".".equals(myText)) {
          resolved = context;
        }
        else if ("..".equals(myText)) {
          resolved = ((WebDirectoryElement)context).getParentDirectory();
        }
        else {
          WebDirectoryElement[] children = ((WebDirectoryElement)context).getChildren();

          for (WebDirectoryElement child : children) {
            if (equalsTo(child)) {
              resolved = child.isDirectory() ? (PsiFileSystemItem)child : child.getOriginalFile();
              break;
            }
          }
        }
      }
      else if (context instanceof PsiDirectory) {
        if (".".equals(myText)) {
          resolved = context;
        }
        else if ("..".equals(myText)) {
          resolved = ((PsiDirectory)context).getParentDirectory();
        }
        else {
          PsiElement[] children = context.getChildren();

          for (PsiElement element : children) {
            PsiFileSystemItem child = (PsiFileSystemItem)element;

            if (equalsTo(child)) {
              resolved = child;
              break;
            }
          }
        }
      }
      if (resolved != null) {
        result.add(new PsiElementResolveResult(resolved));
      }
    }
    return result.toArray(new ResolveResult[result.size()]);
  }

  public Object[] getVariants(){
    try{
      final List ret = new ArrayList();
      final PsiScopeProcessor proc = myFileReferenceSet.createProcessor(ret, getSoftenType());
      processVariants(proc);
      return ret.toArray();
    }
    catch(ProcessorRegistry.IncompatibleReferenceTypeException e){
      LOG.error(e);
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }

  public void processVariants(@NotNull final PsiScopeProcessor processor) {
    final Collection<PsiElement> contexts = getContexts();
    for (PsiElement context : contexts) {
      if (context instanceof WebDirectoryElement) {
        WebDirectoryElement[] children = ((WebDirectoryElement)context).getChildren();
        for (WebDirectoryElement child : children) {
          PsiFileSystemItem item = child.isDirectory() ? (PsiFileSystemItem)child : child.getOriginalFile();
          if (!processor.execute(item, PsiSubstitutor.EMPTY)) return;
        }
      } else if (context instanceof PsiDirectory) {
        final PsiElement[] children = context.getChildren();

        for (PsiElement child : children) {
          PsiFileSystemItem item = (PsiFileSystemItem)child;
          if (!processor.execute(item, PsiSubstitutor.EMPTY)) return;
        }
      }
      else if(getContextReference() == null){
        myFileReferenceSet.getProvider().handleEmptyContext(processor, getElement());
      }
    }

  }

  private FileReference getContextReference(){
    return myIndex > 0 ? myFileReferenceSet.getReference(myIndex - 1) : null;
  }

  public ReferenceType getType(){
    return myFileReferenceSet.getType(myIndex);
  }

  private static ReferenceType getSoftenType(){
    return new ReferenceType(new int[] {ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.FILE, ReferenceType.DIRECTORY});
  }

  public PsiElement getElement(){
    return myFileReferenceSet.getElement();
  }

  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  private boolean equalsTo(final PsiFileSystemItem child) {
    return myFileReferenceSet.isCaseSensitive() ? myText.equals(child.getName()) :
           myText.compareToIgnoreCase(child.getName()) == 0;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof WebDirectoryElement || element instanceof PsiFile || element instanceof PsiDirectory) {
      return element.getManager().areElementsEquivalent(element, resolve());
    }
    return false;
  }

  public TextRange getRangeInElement(){
    return myRange;
  }

  public String getCanonicalText(){
    return myText;
  }

  public boolean isSoft(){
    return myFileReferenceSet.isSoft();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator != null) {
      myFileReferenceSet.setElement(manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName));
      //Correct ranges
      int delta = newElementName.length() - myRange.getLength();
      myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
      FileReference[] references = myFileReferenceSet.getAllReferences();
      for (int idx = myIndex + 1; idx < references.length; idx++) {
        references[idx].myRange = references[idx].myRange.shiftRight(delta);
      }
      return myFileReferenceSet.getElement();
    }
    throw new IncorrectOperationException("Manipulator for this element is not defined");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
    if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element");

    final PsiFile file = getElement().getContainingFile();
    final WebModuleProperties properties = (WebModuleProperties)WebUtil.getWebModuleProperties(file);
    final String newName;
    if (properties != null) {
      newName = JspUtil.getDeploymentPath((PsiFileSystemItem)element);
    } else {
      final VirtualFile dst = element.getContainingFile().getVirtualFile();
      if (dst == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);
      final VirtualFile currentFile = file.getVirtualFile();
      LOG.assertTrue(currentFile != null);
      newName = VfsUtil.getPath(currentFile, dst, '/');
      if (newName == null) {
        throw new IncorrectOperationException("Cannot find path between files; src = " +
                                              currentFile.getPresentableUrl() + "; dst = " + dst.getPresentableUrl());
      }
    }
    final TextRange range = new TextRange(myFileReferenceSet.getStartInElement(), getRangeInElement().getEndOffset());
    final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
    if (manipulator == null) {
      throw new IncorrectOperationException("Manipulator not defined for: " + getElement());
    }
    return manipulator.handleContentChange(getElement(), range, newName);
  }

  private static ElementManipulator<PsiElement> getManipulator(PsiElement currentElement){
    return ReferenceProvidersRegistry.getInstance(currentElement.getProject()).getManipulator(currentElement);
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    FileReferenceQuickFixProvider.registerQuickFix(info, reference);
  }

  public FileReferenceSet getFileReferenceSet() {
    return myFileReferenceSet;
  }
}
