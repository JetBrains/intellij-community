/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiClassReferenceListStubImpl extends StubBase<PsiReferenceList> implements PsiClassReferenceListStub {
  private final PsiReferenceList.Role myRole;
  private final String[] myNames;
  private PsiClassType[] myTypes;

  public PsiClassReferenceListStubImpl(final JavaClassReferenceListElementType type, final StubElement parent, final String[] names, final PsiReferenceList.Role role) {
    super(parent, type);
    myNames = names;
    myRole = role;
  }

  public PsiClassType[] getReferencedTypes() {
    if (myTypes != null) return myTypes;

    PsiClassType[] types = new PsiClassType[myNames.length];

    final boolean compiled = ((JavaClassReferenceListElementType)getStubType()).isCompiled(this);
    if (compiled) {
      for (int i = 0; i < types.length; i++) {
        types[i] = new PsiClassReferenceType(new ClsJavaCodeReferenceElementImpl(getPsi(), myNames[i]));
      }
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();

      int nullcount = 0;
      final PsiReferenceList psi = getPsi();
      PsiManager manager = psi.getManager();
      for (int i = 0; i < types.length; i++) {
        PsiElement context = psi;
        if (getParentStub() instanceof PsiClassStub) {
          context = ((PsiClassImpl)getParentStub().getPsi()).calcBasesResolveContext(PsiNameHelper.getShortClassName(myNames[i]), psi);
        }

        final FileElement holderElement = DummyHolderFactory.createHolder(manager, context).getTreeElement();
        final PsiJavaCodeReferenceElementImpl ref =
            (PsiJavaCodeReferenceElementImpl)Parsing.parseJavaCodeReferenceText(manager, myNames[i], holderElement.getCharTable());
        if (ref != null) {
          TreeUtil.addChildren(holderElement, ref);
          ref.setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);
          types[i] = factory.createType(ref);
        }
        else {
          types[i] = null;
          nullcount++;
        }
      }

      if (nullcount > 0) {
        PsiClassType[] newtypes = new PsiClassType[types.length - nullcount];
        int cnt = 0;
        for (PsiClassType type : types) {
          if (type != null) newtypes[cnt++] = type;
        }
        types = newtypes;
      }
    }

    myTypes = types;
    return types;
  }

  public String[] getReferencedNames() {
    return myNames;
  }

  public PsiReferenceList.Role getRole() {
    return myRole;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiRefListStub[").append(myRole.name()).append(":");
    for (int i = 0; i < myNames.length; i++) {
      if (i > 0) builder.append(", ");
      builder.append(myNames[i]);
    }
    builder.append("]");
    return builder.toString();
  }
}