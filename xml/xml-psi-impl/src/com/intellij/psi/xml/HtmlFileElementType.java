// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;

import java.util.Arrays;

public class HtmlFileElementType extends IStubFileElementType<PsiFileStub<?>> {

  private static volatile int stubVersion = -1;
  
  public HtmlFileElementType() {super("html.file", HTMLLanguage.INSTANCE);}

  @Override
  public int getStubVersion() {
    return getHtmlStubVersion() + 3;
  }

  public static int getHtmlStubVersion() {
    int version = stubVersion;
    if (version != -1) return version;
    IElementType[] dataElementTypes = IElementType.enumerate(
      (elementType) -> elementType instanceof IStubFileElementType && isAcceptable(elementType));

    int res = Arrays.stream(dataElementTypes).mapToInt((e) -> ((IStubFileElementType<?>)e).getStubVersion()).sum();
    stubVersion = res;
    
    return res;
  }

  public static boolean isAcceptable(IElementType elementType) {
    String id = elementType.getLanguage().getID();
    
    //hardcoded values as in BaseHtmlLexer
    //js and css dialect uses the same stub id as the parent language
    return id.equals("JavaScript") || id.equals("CSS");
  }
}
