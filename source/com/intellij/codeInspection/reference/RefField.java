/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:10 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

public class RefField extends RefElement {
  private static final int USED_FOR_READING_MASK = 0x10000;
  private static final int USED_FOR_WRITING_MASK = 0x20000;
  private static final int ASSIGNED_ONLY_IN_INITIALIZER = 0x40000;

  public RefField(PsiField field, RefManager manager) {
      this((RefClass) manager.getReference(field.getContainingClass()), field, manager);
  }

  public RefField(RefClass ownerClass, PsiField field, RefManager manager) {
    super(field, manager);

    ownerClass.add(this);

    PsiClass psiClass = field.getContainingClass();

    if (psiClass.isInterface()) {
      setIsStatic(true);
      setIsFinal(true);
    }

    setCanBeStatic(isStatic());
  }

  protected void markReferenced(RefElement refFrom, PsiElement psiFrom, PsiElement psiWhat, boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    super.markReferenced(refFrom, psiFrom, psiWhat, forWriting, forReading, expressionFrom);

    boolean referencedFromClassInitializer = false;

    if (forWriting && expressionFrom != null) {
      PsiClassInitializer initializer = PsiTreeUtil.getParentOfType(expressionFrom, PsiClassInitializer.class);
      if (initializer != null) {
        if (initializer.getParent() instanceof PsiClass && psiFrom == initializer.getParent()) {
          referencedFromClassInitializer = true;
        }
      }
    }

    if (forWriting) {
      setUsedForWriting(true);
      if (!(refFrom instanceof RefMethod) ||
          !((RefMethod)refFrom).isConstructor() ||
          ((PsiField) psiWhat).hasInitializer()) {
        if (!referencedFromClassInitializer) {
          setCanBeFinal(false);
        }
      }
    }

    if (forReading) {
      setUsedForReading(true);
    }
  }

  public boolean isUsedForReading() {
    return checkFlag(USED_FOR_READING_MASK);
  }

  private void setUsedForReading(boolean usedForReading) {
    setFlag(usedForReading, USED_FOR_READING_MASK);
  }

  public boolean isUsedForWriting() {
    return checkFlag(USED_FOR_WRITING_MASK);
  }

  private void setUsedForWriting(boolean usedForWriting) {
    setFlag(false, ASSIGNED_ONLY_IN_INITIALIZER);
    setFlag(usedForWriting, USED_FOR_WRITING_MASK);
  }

  public boolean isOnlyAssignedInInitializer() {
    return checkFlag(ASSIGNED_ONLY_IN_INITIALIZER);
  }

  public void accept(RefVisitor visitor) {
    visitor.visitField(this);
  }

  public void buildReferences() {
    PsiField psiField = (PsiField) getElement();
    if (psiField != null) {
      RefUtil.addReferences(psiField, this, psiField.getInitializer());

      if (psiField instanceof PsiEnumConstant) {
        RefUtil.addReferences(psiField, this, psiField);
      }

      if (psiField.getInitializer() != null || psiField instanceof PsiEnumConstant) {
        if (!checkFlag(USED_FOR_WRITING_MASK)) {
          setFlag(true, ASSIGNED_ONLY_IN_INITIALIZER);
          setFlag(true, USED_FOR_WRITING_MASK);
        }
      }
      PsiType type = psiField.getType();
      if (type != null) {
        PsiType psiType = type;
        RefClass ownerClass = RefUtil.getOwnerClass(getRefManager(), psiField);

        if (ownerClass != null) {
          psiType = psiType.getDeepComponentType();
          if (psiType instanceof PsiClassType) {
            PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
            if (psiClass != null && RefUtil.belongsToScope(psiClass, getRefManager())) {
                RefClass refClass = (RefClass) getRefManager().getReference(psiClass);
              if (refClass != null) {
                refClass.addTypeReference(ownerClass);
                refClass.addClassExporter(this);
              }
            }
          }
        }
      }
    }
  }

  public RefClass getOwnerClass() {
    return (RefClass) getOwner();
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        PsiField psiField = (PsiField) getElement();
        result[0] = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME |
          PsiFormatUtil.SHOW_FQ_NAME |
          PsiFormatUtil.SHOW_CONTAINING_CLASS,
            PsiSubstitutor.EMPTY);
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  @Nullable
  public static RefField fieldFromExternalName(RefManager manager, String externalName) {
    RefField refField = null;

    int lastDotIdx = externalName.lastIndexOf('.');
    if (lastDotIdx > 0 && lastDotIdx < externalName.length() - 2) {
      String className = externalName.substring(0, lastDotIdx);
      String fieldName = externalName.substring(lastDotIdx + 1);

      if (RefClass.classFromExternalName(manager, className) != null) {
        PsiClass psiClass = PsiManager.getInstance(manager.getProject()).findClass(className);
        if (psiClass != null) {
          PsiField psiField = psiClass.findFieldByName(fieldName, false);

          if (psiField != null) {
              refField = (RefField) manager.getReference(psiField);
          }
        }
      }
    }

    return refField;
  }

  public boolean isSuspicious() {
    if (isEntry()) return false;
    if (super.isSuspicious()) return true;
    return isUsedForReading() != isUsedForWriting();
  }
}
