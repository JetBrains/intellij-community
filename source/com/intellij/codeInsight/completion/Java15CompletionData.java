package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.getters.EnumConstantsGetter;
import com.intellij.psi.filters.getters.AnnotationMethodsGetter;
import com.intellij.psi.filters.position.*;

/**
 * @author ven
 */
public class Java15CompletionData extends JavaCompletionData {
  protected void initVariantsInFileScope() {
    super.initVariantsInFileScope();
    //static keyword in static import
    {
      final CompletionVariant variant = new CompletionVariant(PsiImportList.class, new LeftNeighbour(new TextFilter ("import")));
      variant.addCompletion(PsiKeyword.STATIC, TailType.SPACE);

      this.registerVariant(variant);
    }

    {
      final ElementFilter position = new AndFilter(new LeftNeighbour(new TextFilter("@")),
                                                   new NotFilter(new SuperParentFilter(
                                                     new OrFilter(new ElementFilter[] {
                                                       new ClassFilter(PsiNameValuePair.class),
                                                       new ClassFilter(PsiParameterList.class)
                                                     }
                                                     )))
                                                   );

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.INTERFACE, TailType.SPACE);

      registerVariant(variant);
    }

    {
      final ElementFilter position = new OrFilter(new ElementFilter[]{
        END_OF_BLOCK,
        new LeftNeighbour(new TextFilter(MODIFIERS_LIST)),
        new StartElementFilter()
      });

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      variant.includeScopeClass(PsiClass.class);

      variant.addCompletion(PsiKeyword.ENUM, TailType.SPACE);
      registerVariant(variant);
    }

    {
      final ElementFilter position = new ElementFilter() {
        public boolean isClassAcceptable(Class hintClass){
          return true;
        }
      
        public boolean isAcceptable(Object element, PsiElement context){
          if (! (element instanceof PsiElement)) return false;
          final PsiElement parent = ((PsiElement)element).getParent();
  
          final PsiElement grandparent = (parent != null)?parent.getParent():null;
          return (parent instanceof PsiNameValuePair ||
                  grandparent instanceof PsiNameValuePair
                 );
        }
      };

      final CompletionVariant variant = new CompletionVariant(PsiJavaFile.class, position);
      //variant.includeScopeClass(PsiReferenceExpression.class);
      variant.includeScopeClass(PsiNameValuePair.class);

      variant.addCompletion(new AnnotationMethodsGetter(), TailType.NONE);
      registerVariant(variant);
    }

    {
      final ElementFilter position = new ScopeFilter(new ParentElementFilter(new AndFilter(
        new ClassFilter(PsiSwitchLabelStatement.class),
        new ParentElementFilter(
          new PositionElementFilter() {
            public boolean isAcceptable(Object element, PsiElement context) {
              final PsiExpression expression = ((PsiSwitchStatement)element).getExpression();
              if(expression == null) return false;
              final PsiType type = expression.getType();
              return type instanceof PsiClassType;
            }
          }, 2)
      )));
      final CompletionVariant variant = new CompletionVariant(PsiReferenceExpression.class, position);
      variant.addCompletionFilterOnElement(new ClassFilter(PsiEnumConstant.class), ':');
      registerVariant(variant);
    }
  }

  protected void initVariantsInClassScope() {
    super.initVariantsInClassScope();
    {
      //Completion of "this" & "super" inside wildcards
      final CompletionVariant variant = new CompletionVariant(new AndFilter(new LeftNeighbour(new LeftNeighbour(new TextFilter("<"))), new LeftNeighbour(new TextFilter("?"))));
      variant.includeScopeClass(PsiVariable.class, true);
      variant.addCompletion(PsiKeyword.SUPER, TailType.SPACE);
      variant.addCompletion(PsiKeyword.EXTENDS, TailType.SPACE);
      this.registerVariant(variant);
    }
  }
}
