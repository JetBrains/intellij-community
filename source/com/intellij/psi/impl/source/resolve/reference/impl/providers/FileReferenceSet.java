package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.jsp.JspManager;
import com.intellij.psi.impl.source.jsp.JspImplUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import com.intellij.j2ee.j2eeDom.web.WebModuleProperties;

import java.util.List;
import java.util.ArrayList;

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
  private final PsiElement myElement;
  private final int myStartInElement;
  private final ReferenceType myType;
  private final PsiReferenceProvider myProvider;

  public FileReferenceSet(String str, PsiElement element, int startInElement, ReferenceType type, PsiReferenceProvider provider){
    myType = type;
    myElement = element;
    myStartInElement = startInElement;
    myProvider = provider;

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
      if((currentSlash = nextSlash) < 0)
        break;
    }

    myReferences = referencesList.toArray(new Reference[referencesList.size()]);
  }

  private Reference getReference(int index){
    return myReferences[index];
  }

  protected Reference[] getAllReferences(){
    return myReferences;
  }

  private ReferenceType getType(int index){
    if(index != myReferences.length - 1){
      return new ReferenceType(myType, ReferenceType.WEB_DIRECTORY_ELEMENT);
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
      return new ReferenceType(ReferenceType.WEB_DIRECTORY_ELEMENT, ReferenceType.FILE);
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
        for (int i = 0; i < children.length; i++) {
          PsiFileSystemItem item = children[i].isDirectory() ? ((PsiFileSystemItem)children[i]) : children[i].getOriginalFile();
          if (!processor.execute(item, PsiSubstitutor.EMPTY)) return;
        }
      }
      return;
    }

    public PsiElement resolve() {
      final PsiElement context = getContext();

      if (context instanceof WebDirectoryElement) {
        if (".".equals(myText)) return context;
        if ("..".equals(myText)) return ((WebDirectoryElement)context).getParentDirectory();
        WebDirectoryElement[] children = ((WebDirectoryElement)context).getChildren();

        for (int i = 0; i < children.length; i++) {
          WebDirectoryElement child = children[i];
          if (myText.equals(child.getName())) {
            return child.isDirectory() ? ((PsiFileSystemItem)child) : child.getOriginalFile();
          }
        }
      }

      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      if(element instanceof WebDirectoryElement || element instanceof PsiFile)
        return super.isReferenceTo(element);
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
      throw new IncorrectOperationException("NYI");
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException{
      throw new IncorrectOperationException("NYI");
    }
  }

  protected boolean isSoft(){
    return false;
  }

  public static WebDirectoryElement getDefaultContext (PsiElement  element) {
    if (element instanceof XmlAttributeValue) {
      XmlAttributeValue attribute = (XmlAttributeValue)element;
      Project project = attribute.getProject();
      final String path = attribute.getValue().trim();
      PsiFile file = (PsiFile)attribute.getContainingFile();

      if (!(file.isPhysical())) file = (JspFile)file.getOriginalFile();
      if (file == null) return null;
      final WebModuleProperties properties = JspImplUtil.getWebModuleProperties(file);

      if (properties != null) {
        final JspManager jspManager = JspManager.getInstance(project);
        if (path.startsWith(SEPARATOR_STRING)) {
          return jspManager.findWebDirectoryElementByPath("/", properties);
        }
        else {
          final PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            return jspManager.findWebDirectoryByFile(dir.getVirtualFile(), properties);
          }
        }
      }
    }
    return null;
  }
}
