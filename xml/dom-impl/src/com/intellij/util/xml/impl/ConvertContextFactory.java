package com.intellij.util.xml.impl;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;

public class ConvertContextFactory {
   public static ConvertContext createConvertContext(final DomElement element) {
      return createConvertContext(DomManagerImpl.getDomInvocationHandler(element));
   }

   public static ConvertContext createConvertContext(final DomInvocationHandler element) {
     return new ConvertContextImpl(element);
   }
}
