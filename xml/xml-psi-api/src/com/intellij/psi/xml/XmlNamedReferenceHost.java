// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xml.XmlNamedReferenceProviderBean;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface XmlNamedReferenceHost extends PsiExternalReferenceHost {

  /**
   * There exist host elements with many reference providers,
   * and there is no sense to run all providers against them.
   * The returned string is used to obtain list of providers that actually can contribute something,
   * <p/>
   * <b>Example</b>.
   * Some reference provider only contributes references into XML tags with fixed name "foo".
   * The provider must then specify "foo" in {@link XmlNamedReferenceProviderBean#hostNames}.
   * XML tags implement this method and return their name from it.
   * When references are queried, the platform calls this method,
   * and uses returned string to obtain the provider.
   * In case the XML tag name is actually "foo", then the provider is queried for references.
   */
  @Nullable @NlsSafe String getHostName();
}
