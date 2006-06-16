/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiAnchor;
import com.intellij.psi.*;

public class LazyPointerImpl<E extends PsiElement> implements SmartPointerEx<E> {
  private E myElement = null;
  private PsiAnchor myAnchor = null;
  private SmartPsiElementPointer myPointer = null;
  private Class<? extends PsiElement> myElementClass;

  public LazyPointerImpl(E element) {
    myElementClass = element.getClass();
    if (element instanceof PsiCompiledElement) {
      myElement = element;
    }
    else if (element instanceof PsiMember) {
      myPointer = setupPointer(element);
    }
    else {
      myAnchor = new PsiAnchor(element);
    }
  }

  private static SmartPsiElementPointer setupPointer(PsiElement element) {
    return SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  public void fastenBelt() {
    if (myAnchor != null) {
      final PsiElement element = myAnchor.retrieve();
      if (element != null) {
        myPointer = setupPointer(element);
        ((SmartPointerEx)myPointer).fastenBelt();
        myAnchor = null;
      }
      else myAnchor = null;
    }
  }

  public void documentAndPsiInSync() {
  }

  public E getElement() {
    if (myElement != null) return myElement.isValid() ? myElement : null;
    if (myPointer != null) return (E) myPointer.getElement();
    final PsiElement psiElement = myAnchor.retrieve();
    if (psiElement != null) {
      return myElementClass.isAssignableFrom(psiElement.getClass()) ? (E) psiElement : null;
    }

    return null;
  }
}