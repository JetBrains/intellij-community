/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:28:53 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;

public abstract class RefElement extends RefEntity {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.reference.RefElement");
  private static final int ACCESS_MODIFIER_MASK = 0x03;
  private static final int ACCESS_PRIVATE = 0x00;
  private static final int ACCESS_PROTECTED = 0x01;
  private static final int ACCESS_PACKAGE = 0x02;
  private static final int ACCESS_PUBLIC = 0x03;

  private static final int IS_STATIC_MASK = 0x04;
  private static final int IS_FINAL_MASK = 0x08;
  private static final int IS_CAN_BE_STATIC_MASK = 0x10;
  private static final int IS_CAN_BE_FINAL_MASK = 0x20;
  private static final int IS_REACHABLE_MASK = 0x40;
  private static final int IS_ENTRY_MASK = 0x80;
  private static final int IS_PERMANENT_ENTRY_MASK = 0x100;
  private static final int IS_USES_DEPRECATION_MASK = 0x200;


  private SmartPsiElementPointer myID;
  private final RefManager myManager;

  private final ArrayList<RefElement> myOutReferences;
  private final ArrayList<RefElement> myInReferences;

  private int myFlags;
  private boolean myIsDeleted ;

  protected RefElement(String name, RefElement owner) {
    super(name);
    myManager = owner.getRefManager();
    myID = null;
    myFlags = 0;

    myOutReferences = new ArrayList<RefElement>(0);
    myInReferences = new ArrayList<RefElement>(0);

    String am = owner.getAccessModifier();
    final int access_id;

    if (PsiModifier.PRIVATE.equals(am)) {
      access_id = ACCESS_PRIVATE;
    }
    else if (PsiModifier.PUBLIC.equals(am)) {
      access_id = ACCESS_PUBLIC;
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(am)) {
      access_id = ACCESS_PACKAGE;
    }
    else {
      access_id = ACCESS_PROTECTED;
    }

    myFlags = ((myFlags >> 2) << 2) | access_id;
  }

  protected RefElement(PsiModifierListOwner elem, RefManager manager) {
    super(RefUtil.getName(elem));
    myManager = manager;
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(elem);
    myFlags = 0;

    setCanBeStatic(true);
    setCanBeFinal(true);

    setAccessModifier(RefUtil.getAccessModifier(elem));

    myOutReferences = new ArrayList<RefElement>(0);
    myInReferences = new ArrayList<RefElement>(0);

    initialize(elem);
  }

  protected void initialize(PsiModifierListOwner elem) {
    setIsStatic(elem.hasModifierProperty(PsiModifier.STATIC));
    setIsFinal(elem.hasModifierProperty(PsiModifier.FINAL));
  }

  public boolean isValid() {
    if (myIsDeleted) return false;
    final PsiElement element = getElement();
    return element != null && element.isPhysical();
  }

  public RefManager getRefManager() {
    return myManager;
  }

  public String getExternalName() {
    return getName();
  }

  public PsiModifierListOwner getElement() {
    return (PsiModifierListOwner)myID.getElement();
  }

  public abstract void accept(RefVisitor visitor);

  public void buildReferences() {
  }

  protected void markReferenced(final RefElement refFrom, PsiElement psiFrom, PsiElement psiWhat, final boolean forWriting, boolean forReading, PsiReferenceExpression expressionFrom) {
    addInReference(refFrom);
  }

  public boolean isReachable() {
    return checkFlag(IS_REACHABLE_MASK);
  }

  public boolean isReferenced() {
    return getInReferences().size() > 0;
  }

  public boolean hasSuspiciousCallers() {
    for (Iterator<RefElement> iterator = getInReferences().iterator(); iterator.hasNext();) {
      RefElement refCaller = iterator.next();
      if (refCaller.isSuspicious()) return true;
    }

    return false;
  }

  public boolean isSuspiciousRecursive() {
    return isCalledOnlyFrom(this, new Stack<RefElement>());
  }

  private boolean isCalledOnlyFrom(RefElement refElement, Stack<RefElement> callStack) {
    if (callStack.contains(this)) return refElement == this ? true : false;
    if (getInReferences().size() == 0) return false;

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;
      for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        if (refSuper.getInReferences().size() > 0) return false;
      }
    }

    callStack.push(this);
    for (Iterator<RefElement> iterator = getInReferences().iterator(); iterator.hasNext();) {
      RefElement refCaller = iterator.next();
      if (!refCaller.isSuspicious() || !refCaller.isCalledOnlyFrom(refElement, callStack)) {
        callStack.pop();
        return false;
      }
    }

    callStack.pop();
    return true;
  }

  public void addReference(RefElement refWhat, PsiElement psiWhat, PsiElement psiFrom, boolean forWriting, boolean forReading, PsiReferenceExpression expression) {
    if (refWhat != null) {
      addOutReference(refWhat);
      refWhat.markReferenced(this, psiFrom, psiWhat, forWriting, forReading, expression);
    }
  }

  public Collection<RefElement> getOutReferences() {
    return myOutReferences;
  }

  public Collection<RefElement> getInReferences() {
    return myInReferences;
  }

  public void addInReference(RefElement refElement) {
    if (!myInReferences.contains(refElement)) {
      myInReferences.add(refElement);
    }
  }

  public void addOutReference(RefElement refElement) {
    if (!myOutReferences.contains(refElement)) {
      myOutReferences.add(refElement);
    }
  }

  public boolean isFinal() {
    return checkFlag(IS_FINAL_MASK);
  }

  public boolean isStatic() {
    return checkFlag(IS_STATIC_MASK);
  }

  public void setIsStatic(boolean isStatic) {
    setFlag(isStatic, IS_STATIC_MASK);
  }

  protected boolean checkFlag(int mask) {
    return (myFlags & mask) != 0;
  }

  protected void setFlag(boolean b, int mask) {
    if (b) {
      myFlags |= mask;
    } else {
      myFlags &= ~mask;
    }
  }

  public void setCanBeStatic(boolean canBeStatic) {
    setFlag(canBeStatic, IS_CAN_BE_STATIC_MASK);
  }

  public boolean isCanBeStatic() {
    return checkFlag(IS_CAN_BE_STATIC_MASK);
  }

  public void setCanBeFinal(boolean canBeFinal) {
    setFlag(canBeFinal, IS_CAN_BE_FINAL_MASK);
  }

  public boolean isCanBeFinal() {
    return checkFlag(IS_CAN_BE_FINAL_MASK);
  }

  public boolean isUsesDeprecatedApi() {
    return checkFlag(IS_USES_DEPRECATION_MASK);
  }

  public void setUsesDeprecatedApi(boolean usesDeprecatedApi) {
    setFlag(usesDeprecatedApi, IS_USES_DEPRECATION_MASK);
  }

  public void setIsFinal(boolean isFinal) {
    setFlag(isFinal, IS_FINAL_MASK);
  }

  public void setEntry(boolean entry) {
    setFlag(entry, IS_ENTRY_MASK);
  }

  public boolean isEntry() {
    return checkFlag(IS_ENTRY_MASK);
  }

  public boolean isPermanentEntry() {
    return checkFlag(IS_PERMANENT_ENTRY_MASK);
  }

  public void setPermanentEntry(boolean permanentEntry) {
    setFlag(permanentEntry, IS_PERMANENT_ENTRY_MASK);
  }

  public void setReachable(boolean reachable) {
    setFlag(reachable, IS_REACHABLE_MASK);
  }

  public boolean isSuspicious() {
    return !isReachable();
  }

  public String getAccessModifier() {
    int access_id = myFlags & ACCESS_MODIFIER_MASK;
    if (access_id == ACCESS_PRIVATE) return PsiModifier.PRIVATE;
    if (access_id == ACCESS_PUBLIC) return PsiModifier.PUBLIC;
    if (access_id == ACCESS_PACKAGE) return PsiModifier.PACKAGE_LOCAL;
    return PsiModifier.PROTECTED;
  }

  public void setAccessModifier(String am) {
    final int access_id;

    if (PsiModifier.PRIVATE.equals(am)) {
      access_id = ACCESS_PRIVATE;
    }
    else if (PsiModifier.PUBLIC.equals(am)) {
      access_id = ACCESS_PUBLIC;
    }
    else if (PsiModifier.PACKAGE_LOCAL.equals(am)) {
      access_id = ACCESS_PACKAGE;
    }
    else {
      access_id = ACCESS_PROTECTED;
    }

    myFlags = ((myFlags >> 2) << 2) | access_id;
  }

  public void referenceRemoved() {
    myIsDeleted = true;
    if (getOwner() != null) {
      getOwner().removeChild(this);
    }

    for (Iterator<RefElement> iterator = getOutReferences().iterator(); iterator.hasNext();) {
      RefElement refCallee = iterator.next();
      refCallee.getInReferences().remove(this);
    }

    for (Iterator<RefElement> iterator = getInReferences().iterator(); iterator.hasNext();) {
      RefElement refCaller = iterator.next();
      refCaller.getOutReferences().remove(this);
    }
  }

  public URL getURL() {
    try {
      return new URL(getElement().getContainingFile().getVirtualFile().getUrl() + "#" + getElement().getTextRange().getStartOffset());
    } catch (MalformedURLException e) {
      LOG.error(e);
    }

    return null;
  }
}
