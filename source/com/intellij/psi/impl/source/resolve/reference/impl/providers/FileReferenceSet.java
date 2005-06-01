package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;
import com.intellij.j2ee.module.view.web.WebUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.jsp.JspUtil;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 16, 2005
 * Time: 9:43:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class FileReferenceSet {
  private static final char SEPARATOR = '/';
  private static final String SEPARATOR_STRING = "/";

  private Reference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private final ReferenceType myType;
  private final PsiReferenceProvider myProvider;
  private final boolean myCaseSensitive;
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

  private void reparse(String str){
    final List<Reference> referencesList = new ArrayList<Reference>();
    int currentSlash = -1;
    while(currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == ' ') currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;
    Reference currentContextRef;

    while(true){
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      currentContextRef = new Reference(new TextRange(myStartInElement + currentSlash + 1,
                                                          myStartInElement + (nextSlash > 0 ? nextSlash : str.length())),
                                            index++, subreferenceText);
      referencesList.add(currentContextRef);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    myReferences = referencesList.toArray(new Reference[referencesList.size()]);
  }

  private Reference getReference(int index){
    return myReferences[index];
  }

  public Reference[] getAllReferences(){
    return myReferences;
  }

  private ReferenceType getType(int index){
    if(index != myReferences.length - 1){
      return new ReferenceType(new int[] {myType.getPrimitives()[0], ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.DIRECTORY});
    }
    return myType;
  }

  public class Reference extends GenericReference{
    private final int myIndex;
    private TextRange myRange;
    private final String myText;

    public Reference(TextRange range, int index, String text){
      super(myProvider);
      myIndex = index;
      myRange = range;
      myText = text;
    }

    public PsiElement getContext(){
      final PsiReference contextRef = getContextReference();
      return contextRef != null ? contextRef.resolve() : getDefaultContext(myElement);
    }

    public PsiReference getContextReference(){
      return myIndex > 0 ? getReference(myIndex - 1) : null;
    }

    public ReferenceType getType(){
      return FileReferenceSet.this.getType(myIndex);
    }

    public ReferenceType getSoftenType(){
      return new ReferenceType(new int[] {ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.FILE, ReferenceType.DIRECTORY});
    }

    public boolean needToCheckAccessibility() {
      return false;
    }

    public PsiElement getElement(){
      return myElement;
    }

    public void processVariants(final PsiScopeProcessor processor) {
      final PsiElement context = getContext();

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
    }

    public PsiElement resolve() {
      final PsiElement context = getContext();

      if (context instanceof WebDirectoryElement) {
        if (".".equals(myText)) return context;
        if ("..".equals(myText)) return ((WebDirectoryElement)context).getParentDirectory();
        WebDirectoryElement[] children = ((WebDirectoryElement)context).getChildren();

        for (WebDirectoryElement child : children) {
          if (equalsTo(child)) {
            return child.isDirectory() ? (PsiFileSystemItem)child : child.getOriginalFile();
          }
        }
      } else if (context instanceof PsiDirectory) {
        if (".".equals(myText)) return context;
        if ("..".equals(myText)) return ((PsiDirectory)context).getParentDirectory();
        PsiElement[] children = context.getChildren();

        for (PsiElement element : children) {
          PsiFileSystemItem child = (PsiFileSystemItem)element;

          if (equalsTo(child)) {
            return child;
          }
        }
      }

      return null;
    }

    private boolean equalsTo(final PsiFileSystemItem child) {
      return myCaseSensitive ? myText.equals(child.getName()) :
        myText.compareToIgnoreCase(child.getName()) == 0;
    }

    public boolean isReferenceTo(PsiElement element) {
      if (element instanceof WebDirectoryElement || element instanceof PsiFile || element instanceof PsiDirectory) {
        return super.isReferenceTo(element);
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
      return FileReferenceSet.this.isSoft();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException{
      final ElementManipulator<PsiElement> manipulator = getManipulator(getElement());
      if (manipulator != null) {
        myElement = manipulator.handleContentChange(getElement(), getRangeInElement(), newElementName);
        //Correct ranges
        int delta = newElementName.length() - myRange.getLength();
        myRange = new TextRange(getRangeInElement().getStartOffset(), getRangeInElement().getStartOffset() + newElementName.length());
        for (int idx  = myIndex + 1; idx < myReferences.length; idx++) {
          myReferences[idx].myRange = myReferences[idx].myRange.shiftRight(delta);
        }
        return myElement;
      }
      throw new IncorrectOperationException("Manipulator for this element is not defined");
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
      if (!(element instanceof PsiFileSystemItem)) throw new IncorrectOperationException("Cannot bind to element");

      final String newName = JspUtil.getDeploymentPath((PsiFileSystemItem)element);
      final TextRange range = new TextRange(myStartInElement, getRangeInElement().getEndOffset());
      final PsiElement finalElement = getManipulator(getElement()).handleContentChange(getElement(), range, newName);
      return finalElement;
    }
  }

  protected boolean isSoft(){
    return false;
  }

  public PsiElement getDefaultContext (PsiElement element) {
    Project project = element.getProject();
    PsiFile file = element.getContainingFile();

    if (!file.isPhysical()) file = file.getOriginalFile();
    if (file == null) return null;
    final WebModuleProperties properties = (WebModuleProperties)WebUtil.getWebModuleProperties(file);

    if (myPathString.startsWith(SEPARATOR_STRING)) {
      if (properties != null) {
        return JspManager.getInstance(project).findWebDirectoryElementByPath("/", properties);
      } else {
        final VirtualFile contentRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file.getVirtualFile());
        if (contentRootForFile!=null) {
          return PsiManager.getInstance(project).findDirectory(contentRootForFile);
        }
      }
    }
    else {
      final PsiDirectory dir = file.getContainingDirectory();
      if (dir != null) {
        if (properties != null) {
          return JspManager.getInstance(project).findWebDirectoryByFile(dir.getVirtualFile(), properties);
        } else {
          return dir;
        }
      }
    }

    return null;
  }
}
