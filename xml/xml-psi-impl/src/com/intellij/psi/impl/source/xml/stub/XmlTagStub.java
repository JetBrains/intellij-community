// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml.stub;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public interface XmlTagStub<T extends XmlTag> extends StubElement<T> {
  @NotNull @NlsSafe String getName();
}
