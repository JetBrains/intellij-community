package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;

import java.util.*;

public class DocumentFoldingInfo implements JDOMExternalizable, CodeFoldingState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.DocumentFoldingInfo");

  private final Project myProject;
  private final Document myDocument;

  private ArrayList mySmartPointersOrRangeMarkers = new ArrayList();
  private ArrayList<Boolean> myExpandedStates = new ArrayList<Boolean>();
  private Map<RangeMarker, String> myPlaceholderTexts = new HashMap<RangeMarker,String>();
  private static final String DEFAULT_PLACEHOLDER = "...";

  public DocumentFoldingInfo(Project project, Document document) {
    myProject = project;
    myDocument = document;
  }

  public void loadFromEditor(Editor editor) {
    clear();

    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);

    EditorFoldingInfo info = EditorFoldingInfo.get(editor);
    FoldRegion[] foldRegions = editor.getFoldingModel().getAllFoldRegions();
    for (int i = 0; i < foldRegions.length; i++) {
      FoldRegion region = foldRegions[i];
      PsiElement element = info.getPsiElement(region);
      boolean expanded = region.isExpanded();
      boolean collapseByDefault = element != null && FoldingPolicy.isCollapseByDefault(element);
      if (collapseByDefault != !expanded || element == null){
        if (element != null){
          SmartPsiElementPointer pointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
          mySmartPointersOrRangeMarkers.add(pointer);
        }
        else if (region.isValid()){
          mySmartPointersOrRangeMarkers.add(region);
          String placeholderText = region.getPlaceholderText();
          if (placeholderText == null) placeholderText = DEFAULT_PLACEHOLDER;
          myPlaceholderTexts.put(region, placeholderText);
        }
        myExpandedStates.add(expanded ? Boolean.TRUE : Boolean.FALSE);
      }
    }
  }

  void setToEditor(Editor editor) {
    for(int i = 0; i < mySmartPointersOrRangeMarkers.size(); i++){
      Object o = mySmartPointersOrRangeMarkers.get(i);
      if (o instanceof SmartPsiElementPointer){
        SmartPsiElementPointer pointer = (SmartPsiElementPointer)o;
        PsiElement element = pointer.getElement();
        if (element == null || element.getManager().isDisposed()) continue;
        FoldRegion region = FoldingUtil.findFoldRegion(editor, element);
        if (region != null){
          boolean state = myExpandedStates.get(i).booleanValue();
          region.setExpanded(state);
        }
      }
      else if (o instanceof RangeMarker){
        RangeMarker marker = (RangeMarker)o;
        if (!marker.isValid()) continue;
        FoldRegion region = FoldingUtil.findFoldRegion(editor, marker.getStartOffset(), marker.getEndOffset());
        if (region == null) {
          String placeHolderText = myPlaceholderTexts.get(marker);
          region = editor.getFoldingModel().addFoldRegion(marker.getStartOffset(), marker.getEndOffset(), placeHolderText);  //may fail to add in case intersecting region exists
        }

        if (region != null) {
          boolean state = myExpandedStates.get(i).booleanValue();
          region.setExpanded(state);
        }
      }
      else{
        LOG.error("o = " + o);
      }
    }
  }

  public void clear() {
    mySmartPointersOrRangeMarkers.clear();
    myExpandedStates.clear();
    myPlaceholderTexts.clear();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    if (mySmartPointersOrRangeMarkers.size() == 0){
      throw new WriteExternalException();
    }

    String date = null;
    for(int i = 0; i < mySmartPointersOrRangeMarkers.size(); i++){
      Object o = mySmartPointersOrRangeMarkers.get(i);
      Boolean state = myExpandedStates.get(i);
      if (o instanceof SmartPsiElementPointer){
        SmartPsiElementPointer pointer = (SmartPsiElementPointer)o;
        PsiElement psiElement = pointer.getElement();
        if (psiElement == null) continue;
        String signature = FoldingPolicy.getSignature(psiElement);
        if (signature == null) continue;

        PsiElement restoredElement = FoldingPolicy.restoreBySignature(psiElement.getContainingFile(), signature);
        if (!psiElement.equals(restoredElement)){
          restoredElement = FoldingPolicy.restoreBySignature(psiElement.getContainingFile(), signature);
          LOG.assertTrue(false, "element:" + psiElement + ", signature:" + signature + ", file:" + psiElement.getContainingFile());
        }

        Element e = new Element("element");
        e.setAttribute("signature", signature);
        e.setAttribute("expanded", state.toString());
        element.addContent(e);
      } else {
        RangeMarker marker = (RangeMarker) o;
        Element e = new Element("marker");
        if (date == null) {
          date = getTimeStamp();
        }
        if ("".equals(date)) continue;

        e.setAttribute("date", date);
        e.setAttribute("expanded", state.toString());
        String signature = new Integer (marker.getStartOffset()) + ":" + new Integer (marker.getEndOffset());
        e.setAttribute("signature", signature);
        String placeHolderText = myPlaceholderTexts.get(marker);
        e.setAttribute("placeholder", placeHolderText);
        element.addContent(e);
      }
    }
  }

  public void readExternal(Element element) {
    mySmartPointersOrRangeMarkers.clear();
    myExpandedStates.clear();

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (psiFile == null) return;

    String date = null;
    for(Iterator i = element.getChildren().iterator(); i.hasNext();){
      Element e = (Element)i.next();
      if ("element".equals(e.getName())){
        String signature = e.getAttributeValue("signature");
        if (signature == null){
          continue;
        }
        PsiElement restoredElement = FoldingPolicy.restoreBySignature(psiFile, signature);
        if (restoredElement != null){
          mySmartPointersOrRangeMarkers.add(SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(restoredElement));
          myExpandedStates.add(Boolean.valueOf(e.getAttributeValue("expanded")));
        }
      }
      else if ("marker".equals(e.getName())) {
        if (date == null) {
          date = getTimeStamp();
        }
        if ("".equals(date)) continue;

        if (!date.equals(e.getAttributeValue("date")) || FileDocumentManager.getInstance().isDocumentUnsaved(myDocument)) continue;
        StringTokenizer tokenizer = new StringTokenizer(e.getAttributeValue("signature"), ":");
        try {
          int start = Integer.valueOf(tokenizer.nextToken()).intValue();
          int end = Integer.valueOf(tokenizer.nextToken()).intValue();
          if (start < 0 || end >= myDocument.getTextLength() || start > end) continue;
          RangeMarker marker = myDocument.createRangeMarker(start, end);
          mySmartPointersOrRangeMarkers.add(marker);
          myExpandedStates.add(Boolean.valueOf(e.getAttributeValue("expanded")));
          String placeHolderText = e.getAttributeValue("placeholder");
          if (placeHolderText == null) placeHolderText = DEFAULT_PLACEHOLDER;
          myPlaceholderTexts.put(marker, placeHolderText);
        } catch (NoSuchElementException exc) {
          LOG.error(exc);
          continue;
        }
      }
      else {
        throw new IllegalStateException("unknown tag: " + e.getName());
      }
    }
  }

  private String getTimeStamp() {
    String date;
    FileDocumentManager manager = FileDocumentManager.getInstance();
    VirtualFile vFile = manager.getFile(myDocument);
    if (vFile == null) return "";
    date = new Long(vFile.getTimeStamp()).toString();
    return date;
  }
}
