package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;

class SmartPsiElementPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.psi.impl.smartPointers.SmartPsiElementPointerImpl");

  private E myElement;
  private ElementInfo myElementInfo;
  private final Project myProject;

  private static interface ElementInfo {
    Document getDocumentToSynchronize();

    void documentAndPsiInSync();

    @Nullable
    PsiElement restoreElement();
  }

  public SmartPsiElementPointerImpl(Project project, E element) {
    myProject = project;
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myElement = element;
    myElementInfo = null;

    // Assert document commited.
    PsiFile file = element.getContainingFile();
    if (file != null) {
      PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
      Document doc = documentManager.getCachedDocument(file);
      if (doc != null) {
        //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
        if (!(element instanceof PsiFile)) {
          LOG.assertTrue(!documentManager.isUncommited(doc) || documentManager.isCommitingDocument(doc));
        }
      }
    }
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof SmartPsiElementPointer)) return false;
    SmartPsiElementPointer pointer = (SmartPsiElementPointer)obj;
    return Comparing.equal(pointer.getElement(), getElement());
  }

  public int hashCode() {
    PsiElement element = getElement();
    return element != null ? element.hashCode() : 0;
  }

  public E getElement() {
    if (myElement != null && !myElement.isValid()) {
      if (myElementInfo == null) {
        myElement = null;
      }
      else {
        PsiElement restored = myElementInfo.restoreElement();
        if (restored != null && (!areElementKindEqual(restored, myElement) || !restored.isValid())) {
          restored = null;
        }

        myElement = (E) restored;
      }
    }

    if (myElementInfo != null && myElement != null) {
      Document document = myElementInfo.getDocumentToSynchronize();
      if (document != null && PsiDocumentManager.getInstance(myProject).isUncommited(document)) return myElement; // keep element info if document is modified
    }
    myElementInfo = null;

    return myElement;
  }

  @Nullable
  private ElementInfo createElementInfo() {
    if (myElement instanceof PsiCompiledElement) return null;

    if (myElement.getContainingFile() == null) return null;

    if (myElement instanceof ImplicitVariable){
      return new ImplicitVariableInfo((ImplicitVariable)myElement);
    }
    if (myElement instanceof PsiImportList) {
      return new ImportListInfo((PsiJavaFile)myElement.getContainingFile());
    }
    LOG.assertTrue(myElement.isPhysical(),"Attempting to create smart pointer for non-physical element: " + myElement);
    PsiElement anchor = getAnchor(myElement);
    if (anchor != null) {
      return new AnchorElementInfo(anchor);
    }
    else {
      return new SelfElementInfo(myElement);
    }
  }

  @Nullable
  private static PsiElement getAnchor(PsiElement element) {
    LOG.assertTrue(element.isValid());
    PsiElement anchor = null;
    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        anchor = ((PsiAnonymousClass)element).getBaseClassReference().getReferenceNameElement();
      }
      else {
        anchor = ((PsiClass)element).getNameIdentifier();
      }
    }
    else if (element instanceof PsiMethod) {
      anchor = ((PsiMethod)element).getNameIdentifier();
    }
    else if (element instanceof PsiVariable) {
      anchor = ((PsiVariable)element).getNameIdentifier();
    }
    else if (element instanceof XmlTag) {
      final ASTNode astNode = SourceTreeToPsiMap.psiElementToTree(element);
      if (astNode != null) {
        anchor = SourceTreeToPsiMap.treeElementToPsi(XmlChildRole.START_TAG_NAME_FINDER.findChild(astNode));
      }
    }
    return anchor;
  }

  private static boolean areElementKindEqual(PsiElement element1, PsiElement element2) {
    if (element1 instanceof PsiType) {
      return element2 instanceof PsiType;
    }

    return element1.getClass().equals(element2.getClass()); //?
  }

  public void documentAndPsiInSync() {
    if (myElementInfo != null) {
      myElementInfo.documentAndPsiInSync();
    }
  }

  public void fastenBelt() {
    if (myElementInfo == null && myElement != null && myElement.isValid()) {
      myElementInfo = createElementInfo();
    }
  }

  private static class SelfElementInfo implements ElementInfo {
    protected PsiFile myFile;
    private RangeMarker myMarker;
    private int mySyncStartOffset;
    private int mySyncEndOffset;
    private boolean mySyncMarkerIsValid;
    private Class myType;

    public SelfElementInfo(PsiElement anchor) {
      LOG.assertTrue(anchor.isPhysical());
      myFile = anchor.getContainingFile();
      TextRange range = anchor.getTextRange();

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myFile.getProject());
      Document document = documentManager.getDocument(myFile);
      LOG.assertTrue(!documentManager.isUncommited(document));
      myMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

      mySyncStartOffset = range.getStartOffset();
      mySyncEndOffset = range.getEndOffset();
      mySyncMarkerIsValid = true;
      myType = anchor.getClass();
    }

    public Document getDocumentToSynchronize() {
      return myMarker.getDocument();
    }

    public void documentAndPsiInSync() {
      if (!myMarker.isValid()) {
        mySyncMarkerIsValid = false;
        return;
      }

      mySyncStartOffset = myMarker.getStartOffset();
      mySyncEndOffset = myMarker.getEndOffset();
    }

    public PsiElement restoreElement() {
      if (!mySyncMarkerIsValid) return null;
      if (!myFile.isValid()) return null;

      PsiElement anchor = myFile.findElementAt(mySyncStartOffset);
      if (anchor == null) return null;

      TextRange range = anchor.getTextRange();

      if (range.getStartOffset() != mySyncStartOffset) return null;
      while (range.getEndOffset() < mySyncEndOffset) {
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) break;
        range = anchor.getTextRange();
      }

      while (range.getEndOffset() == mySyncEndOffset && anchor != null && !myType.equals(anchor.getClass())) {
        anchor = anchor.getParent();
        if (anchor == null || anchor.getTextRange() == null) break;
        range = anchor.getTextRange();
      }

      if (range.getEndOffset() == mySyncEndOffset) return anchor;
      return null;
    }
  }

  private static class AnchorElementInfo implements ElementInfo {
    protected PsiFile myFile;
    private RangeMarker myMarker;
    private int mySyncStartOffset;
    private int mySyncEndOffset;
    private boolean mySyncMarkerIsValid;

    public AnchorElementInfo(PsiElement anchor) {
      LOG.assertTrue(anchor.isPhysical());
      myFile = anchor.getContainingFile();
      TextRange range = anchor.getTextRange();

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myFile.getProject());
      Document document = documentManager.getDocument(myFile);
      LOG.assertTrue(!documentManager.isUncommited(document));
      myMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

      mySyncStartOffset = range.getStartOffset();
      mySyncEndOffset = range.getEndOffset();
      mySyncMarkerIsValid = true;
    }

    public Document getDocumentToSynchronize() {
      return myMarker.getDocument();
    }

    public void documentAndPsiInSync() {
      if (!myMarker.isValid()) {
        mySyncMarkerIsValid = false;
        return;
      }

      mySyncStartOffset = myMarker.getStartOffset();
      mySyncEndOffset = myMarker.getEndOffset();
    }

    @Nullable
    public PsiElement restoreElement() {
      if (!mySyncMarkerIsValid) return null;
      if (!myFile.isValid()) return null;

      PsiElement anchor = myFile.findElementAt(mySyncStartOffset);
      if (anchor == null) return null;

      TextRange range = anchor.getTextRange();
      if (range.getStartOffset() != mySyncStartOffset || range.getEndOffset() != mySyncEndOffset) return null;

      if (anchor instanceof PsiIdentifier) {
        PsiElement parent = anchor.getParent();
        if (parent instanceof PsiJavaCodeReferenceElement) { // anonymous class, type
          parent = parent.getParent();
        }

        if (!anchor.equals(getAnchor(parent))) return null;

        return parent;
      }
      else if (anchor instanceof XmlToken) {
        XmlToken token = (XmlToken)anchor;

        if (token.getTokenType() == XmlTokenType.XML_NAME) {
          return token.getParent();
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
    }
  }

  private static class ImportListInfo implements ElementInfo {
    private final PsiJavaFile myFile;

    public ImportListInfo(PsiJavaFile file) {
      myFile = file;
    }

    public PsiElement restoreElement() {
      if (!myFile.isValid()) return null;
      return myFile.getImportList();
    }

    public Document getDocumentToSynchronize() {
      return null;
    }

    public void documentAndPsiInSync() {
    }
  }

  private static class ImplicitVariableInfo implements ElementInfo {
    private final ImplicitVariable myVar;

    public ImplicitVariableInfo(ImplicitVariable var) {
      myVar = var;
    }

    public PsiElement restoreElement() {
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || psiIdentifier.isValid()) return myVar;
      return null;
    }

    public Document getDocumentToSynchronize() {
      return null;
    }

    public void documentAndPsiInSync() {
    }
  }
}
