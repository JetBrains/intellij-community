// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.impl.NegatingComparable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;

public class MavenVersionNegatingWeigher extends LookupElementWeigher {
  public MavenVersionNegatingWeigher() {super("mavenVersionWeigher");}

  @Override
  public Comparable weigh(@NotNull LookupElement element) {
    return new NegatingComparable(new MavenVersionComparable(element.getLookupString()));
  }
}
