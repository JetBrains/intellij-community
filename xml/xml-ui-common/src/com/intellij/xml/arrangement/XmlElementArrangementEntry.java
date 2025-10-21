// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.*;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class XmlElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry, NamespaceAwareArrangementEntry {

  private final ArrangementSettingsToken myType;
  private final String                   myName;
  private final String myNamespace;

  public XmlElementArrangementEntry(@Nullable ArrangementEntry parent,
                                    @NotNull TextRange range,
                                    @NotNull ArrangementSettingsToken type,
                                    @Nullable String name,
                                    @Nullable String namespace,
                                    boolean canBeMatched)
  {
    super(parent, range.getStartOffset(), range.getEndOffset(), canBeMatched);
    myName = name;
    myNamespace = namespace;
    myType = type;
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  @Override
  public @Nullable String getNamespace() {
    return myNamespace;
  }

  @Override
  public @NotNull Set<? extends ArrangementSettingsToken> getTypes() {
    return Collections.singleton(myType);
  }
}
