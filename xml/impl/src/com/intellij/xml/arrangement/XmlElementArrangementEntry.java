package com.intellij.xml.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.*;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
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

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getNamespace() {
    return myNamespace;
  }

  @NotNull
  @Override
  public Set<ArrangementSettingsToken> getTypes() {
    return Collections.singleton(myType);
  }
}
