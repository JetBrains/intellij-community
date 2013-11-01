package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PostfixTemplatesManager implements ApplicationComponent {
  @NotNull private final PostfixTemplateProvider[] myTemplateProviders;

  public PostfixTemplatesManager(@NotNull PostfixTemplateProvider[] providers) {
    myTemplateProviders = providers;
  }



  public boolean getAvailableActions(@NotNull PsiElement positionElement) {

    // simple case: someExpression.postfix
    if (positionElement instanceof PsiIdentifier) {
      final PsiReferenceExpression referenceExpression =
        match(positionElement.getParent(), PsiReferenceExpression.class);
      if (referenceExpression != null) {
        final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
        if (qualifierExpression != null) {
          final PsiType type = qualifierExpression.getType();
          final PsiPrimitiveType primitiveType = match(type, PsiPrimitiveType.class);
          if (primitiveType != null) {
            if (primitiveType == PsiType.BOOLEAN) {


                return true;

            }
          }

          //return true;
        }
      }
    }



    for (PostfixTemplateProvider templateProvider : myTemplateProviders) {

    }

    return true;
  }

  @Nullable
  private <T> T match(Object obj, Class<T> type) {
    if (type.isInstance(obj)) return type.cast(obj);
    return null;
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() { }

  @NotNull
  @Override
  public String getComponentName() {
    return PostfixTemplatesManager.class.getTypeName();
  }
}
