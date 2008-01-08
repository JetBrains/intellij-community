package com.intellij.psi.filters;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChameleonElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 17:45:45
 * To change this template use Options | File Templates.
 */
public class FilterUtil{
  private FilterUtil() {
  }

  @Nullable
  public static PsiType getTypeByElement(PsiElement element, PsiElement context){
    //if(!element.isValid()) return null;
    if(element instanceof PsiType){
      return (PsiType)element;
    }
    if(element instanceof PsiClass){
      return JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createType((PsiClass)element);
    }
    else if(element instanceof PsiMethod){
      return ((PsiMethod)element).getReturnType();
    }
    else if(element instanceof PsiVariable){
      return ((PsiVariable)element).getType();
    }
    else if(element instanceof PsiKeyword){
      if(PsiKeyword.CLASS.equals(element.getText())){
        return PsiType.getJavaLangClass(element.getManager(), element.getResolveScope());
      }
      else if(PsiKeyword.TRUE.equals(element.getText()) || PsiKeyword.FALSE.equals(element.getText())){
        return PsiType.BOOLEAN;
      }
      else if(PsiKeyword.THIS.equals(element.getText())){
        PsiElement previousElement = getPreviousElement(context, false);
        if(previousElement != null && ".".equals(previousElement.getText())){
          previousElement = getPreviousElement(previousElement, false);
          assert previousElement != null;

          final String className = previousElement.getText();
          PsiElement walker = context;
          while(walker != null){
            if(walker instanceof PsiClass && !(walker instanceof PsiAnonymousClass)){
              if(className.equals(((PsiClass)walker).getName()))
                return getTypeByElement(walker, context);
            }
            walker = walker.getContext();
          }
        }
        else{
          final PsiClass owner = PsiTreeUtil.getContextOfType(context, PsiClass.class, true);
          return getTypeByElement(owner, context);
        }
      }
    }
    else if(element instanceof PsiExpression){
      return ((PsiExpression)element).getType();
    }

    return null;
  }

  @Nullable
  public static PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    if(element == null || element.getNode() == null) return null;
    ASTNode leftNeibour = prevLeaf(element.getNode());
    while (leftNeibour != null && (leftNeibour.getElementType() == TokenType.WHITE_SPACE || leftNeibour.getPsi() instanceof PsiComment)){
      leftNeibour = prevLeaf(leftNeibour);
    }
    return leftNeibour != null ? leftNeibour.getPsi() : null;

  }

  @Nullable
  public static ASTNode prevLeaf(final ASTNode leaf) {
    LeafElement leftNeibour = (LeafElement)TreeUtil.prevLeaf(leaf);
    if(leftNeibour instanceof ChameleonElement){
      ChameleonTransforming.transform(leftNeibour);
      return prevLeaf(leftNeibour);
    }
    return leftNeibour;
  }

  @Nullable
  public static PsiElement getPreviousElement(final PsiElement element, boolean skipReference){
    PsiElement prev = element;
    if(element != null){
      if(skipReference){
        prev = searchNonSpaceNonCommentBack(element);
        while(prev != null && prev.getParent() instanceof PsiJavaCodeReferenceElement){
          prev = searchNonSpaceNonCommentBack(prev.getParent());
        }
      }
      else{
        prev = searchNonSpaceNonCommentBack(prev);
      }
    }
    return prev;
  }
}
