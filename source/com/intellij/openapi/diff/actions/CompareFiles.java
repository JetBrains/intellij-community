package com.intellij.openapi.diff.actions;

import com.intellij.ide.DataAccessor;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.diff.ex.DiffContentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.ElementPresentation;

public class CompareFiles extends BaseDiffAction {
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    PsiElement[] elements;
    try {
      elements = ELEMENTS_TO_COMPARE.getNotNull(e.getDataContext());
    }
    catch (DataAccessor.NoDataException e1) {
      presentation.setVisible(false);
      return;
    }
    DiffRequest diffRequest = getDiffRequest(elements);
    if (diffRequest == null) {
      presentation.setVisible(false);
      return;
    }
    ElementPresentation presentation1 = ElementPresentation.forElement(elements[0]);
    ElementPresentation presentation2 = ElementPresentation.forElement(elements[1]);
    ElementPresentation.Noun firstKind = presentation1.getKind();
    ElementPresentation.Noun secondKind = presentation2.getKind();
    if (firstKind.equals(secondKind)) {
      presentation.setText(DiffBundle.message("compare.two.element.type.acton.name", firstKind.getTypeNum()));
    } else presentation.setText(
      DiffBundle.message("compare.element.type.with.element.type.action.name", firstKind.getTypeNum(), secondKind.getTypeNum()));
    final boolean canShow = DiffManager.getInstance().getDiffTool().canShow(diffRequest);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(canShow);      
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(canShow);
    }
  }

  protected DiffRequest getDiffData(DataContext dataContext) throws DataAccessor.NoDataException {
    return getDiffRequest(ELEMENTS_TO_COMPARE.getNotNull(dataContext));
  }

  private DiffRequest getDiffRequest(PsiElement[] elements) {
    ElementPresentation presentation1 = ElementPresentation.forElement(elements[0]);
    ElementPresentation presentation2 = ElementPresentation.forElement(elements[1]);
    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      presentation1.getQualifiedName(), presentation2.getQualifiedName());
    SimpleDiffRequest diffRequest = DiffContentFactory.comparePsiElements(elements[0], elements[1], title);
    if (diffRequest == null) return null;
    diffRequest.setContentTitles(presentation1.getNameWithFQComment(), presentation2.getNameWithFQComment());
    return diffRequest;
  }

  private static final DataAccessor<PsiElement> SECONDARY_SOURCE =
    DataAccessor.createConvertor(new DataAccessor<PsiElement>() {
      public PsiElement getImpl(DataContext dataContext) {
        return (PsiElement)dataContext.getData(DataConstantsEx.SECONDARY_PSI_ELEMENT);
      }
    }, SOURCE_ELEMENT);

  private static final DataAccessor<PsiElement[]> ELEMENTS_TO_COMPARE = new DataAccessor<PsiElement[]>() {
    public PsiElement[] getImpl(DataContext dataContext) throws NoDataException {
      PsiElement[] primaryElements = PRIMARY_SOURCES.from(dataContext);
      if (primaryElements != null && primaryElements.length == 2)
        return primaryElements;
      PsiElement secondaryElement = SECONDARY_SOURCE.getNotNull(dataContext);
      PsiElement primaryElement = PRIMARY_SOURCE.getNotNull(dataContext);
      if (primaryElement == secondaryElement ||
          PsiTreeUtil.isAncestor(primaryElement, secondaryElement, false) ||
          PsiTreeUtil.isAncestor(secondaryElement, primaryElement, false)) return null;
      return new PsiElement[]{primaryElement, secondaryElement};
    }
  };
}
