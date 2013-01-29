package com.intellij.util.xml.impl;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;

public class ConvertContextFactory {
   public static ConvertContext createConvertContext(final DomElement element) {
      return new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)) {
        public DomElement getInvocationElement() {
           return element;
        }
      };
   }

   public static ConvertContext createConvertContext(final DomInvocationHandler element) {
     return new ConvertContextImpl(element);
   }
}
