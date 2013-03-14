package com.intellij.xml.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.TypeAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry {

  private final ArrangementSettingsToken myType;
  private final String                   myName;

  public XmlElementArrangementEntry(@Nullable ArrangementEntry parent,
                                    @NotNull TextRange range,
                                    @NotNull ArrangementSettingsToken type,
                                    @Nullable String name,
                                    boolean canBeMatched)
  {
    super(parent, range.getStartOffset(), range.getEndOffset(), canBeMatched);
    myName = name;
    myType = type;
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public Set<ArrangementSettingsToken> getTypes() {
    return Collections.singleton(myType);
  }
}
