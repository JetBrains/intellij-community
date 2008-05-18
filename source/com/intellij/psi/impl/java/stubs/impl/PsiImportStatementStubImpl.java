/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.Nullable;

public class PsiImportStatementStubImpl extends StubBase<PsiImportStatementBase> implements PsiImportStatementStub {
  private final byte myFlags;
  private final StringRef myText;
  private PatchedSoftReference<PsiJavaCodeReferenceElement> myReference = null;

  private final static int ON_DEMAND = 0x01;
  private final static int STATIC = 0x02;

  public PsiImportStatementStubImpl(final StubElement parent, final String text, final byte flags) {
    this(parent, StringRef.fromString(text), flags);
  }

  public PsiImportStatementStubImpl(final StubElement parent, final StringRef text, final byte flags) {
    super(parent, isStatic(flags) ? JavaStubElementTypes.IMPORT_STATIC_STATEMENT : JavaStubElementTypes.IMPORT_STATEMENT);
    myText = text;
    myFlags = flags;
  }

  public boolean isStatic() {
    return isStatic(myFlags);
  }

  private static boolean isStatic(final byte flags) {
    return (flags & STATIC) != 0;
  }

  public boolean isOnDemand() {
    return (myFlags & ON_DEMAND) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  public String getImportReferenceText() {
    return StringRef.toString(myText);
  }

  @Nullable
  public PsiJavaCodeReferenceElement getReference() {
    PsiJavaCodeReferenceElement ref = myReference != null ? myReference.get() : null;
    if (ref == null) {
      ref = isStatic() ? getStaticReference() : getRegularReference();
      myReference = new PatchedSoftReference<PsiJavaCodeReferenceElement>(ref);
    }
    return ref;
  }

  public static byte packFlags(boolean isOnDemand, boolean isStatic) {
    byte flags = 0;
    if (isOnDemand) {
      flags |= ON_DEMAND;
    }
    if (isStatic) {
      flags |= STATIC;
    }
    return flags;
  }


  @Nullable
  public PsiJavaCodeReferenceElement getStaticReference() {
    PsiJavaCodeReferenceElement refElement;

    PsiManager manager = PsiManager.getInstance(getProject());
    final FileElement holderElement = DummyHolderFactory.createHolder(manager, getPsi()).getTreeElement();
    final JavaParsingContext context = new JavaParsingContext(holderElement.getCharTable(), PsiUtil.getLanguageLevel(getPsi()));
    final String refText = getImportReferenceText();
    if (refText == null) return null;

    CompositeElement parsedRef = Parsing.parseJavaCodeReferenceText(manager, refText, context.getCharTable());
    refElement = (PsiJavaCodeReferenceElement)parsedRef;
    if(refElement == null) return null;

    final boolean onDemand = isOnDemand();
    if (onDemand) {
      TreeUtil.addChildren(holderElement, (TreeElement)refElement);
      ((PsiJavaCodeReferenceElementImpl)refElement).setKindWhenDummy(
        PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);
    }
    else {
      refElement = (PsiImportStaticReferenceElement)context.getImportsTextParsing().convertToImportStaticReference(parsedRef);
      TreeUtil.addChildren(holderElement, (TreeElement)refElement);
    }
    return refElement;
  }

  @Nullable
  public PsiJavaCodeReferenceElement getRegularReference() {
    PsiJavaCodeReferenceElementImpl refElement;
    PsiManager manager = PsiManager.getInstance(getProject());

    final FileElement holderElement = DummyHolderFactory.createHolder(manager, getPsi()).getTreeElement();
    final String refText = getImportReferenceText();
    if (refText == null) return null;

    refElement = (PsiJavaCodeReferenceElementImpl) Parsing.parseJavaCodeReferenceText(manager, refText, holderElement.getCharTable());
    if(refElement == null) return null;

    TreeUtil.addChildren(holderElement, refElement);
    refElement.setKindWhenDummy(
        isOnDemand()
        ? PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND
        : PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);

    return refElement;
  }

}