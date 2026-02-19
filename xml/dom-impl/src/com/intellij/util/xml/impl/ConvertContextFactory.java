// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

public final class ConvertContextFactory {
   public static ConvertContext createConvertContext(final DomElement element) {
      return new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)) {
        @Override
        public @NotNull DomElement getInvocationElement() {
           return element;
        }
      };
   }

   public static ConvertContext createConvertContext(final DomInvocationHandler element) {
     return new ConvertContextImpl(element);
   }
}
