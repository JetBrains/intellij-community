package com.intellij.psi.impl.smartPointers;

import org.jetbrains.annotations.Nullable;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

public class AnchorElementInfoFactory implements SmartPointerElementInfoFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.AnchorElementInfoFactory");

  @Nullable
  public SmartPointerElementInfo createElementInfo(final PsiElement element) {
    PsiElement anchor = getAnchor(element);
    if (anchor != null) {
      return new AnchorElementInfo(anchor);
    }
    return null;
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
      anchor = XmlTagUtil.getStartTagNameElement((XmlTag)element);
    }
    if (anchor != null && !anchor.isPhysical()) return null;
    return anchor;
  }

  private static class AnchorElementInfo implements SmartPointerElementInfo {
    protected final PsiFile myFile;
    private final RangeMarker myMarker;
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
}
