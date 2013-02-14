package com.intellij.xml.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.TypeAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry {

  private final ArrangementEntryType myType;
  private final String myName;

  public XmlElementArrangementEntry(@Nullable ArrangementEntry parent,
                                    @NotNull TextRange range,
                                    @NotNull ArrangementEntryType type,
                                    @Nullable String name,
                                    boolean canBeMatched) {
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
  public Set<ArrangementEntryType> getTypes() {
    return Collections.singleton(myType);
  }

  @NotNull
  public ArrangementEntryType getType() {
    return myType;
  }
}
