package com.intellij.tasks.jira.jql.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * @author Mikhail Golubev
 */
public interface JqlTerminalClause extends JqlElement {
  enum Type {
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    CONTAINS,
    NOT_CONTAINS,
    IS,
    IS_NOT,
    IN,
    NOT_IN,
    WAS,
    WAS_IN,
    WAS_NOT,
    WAS_NOT_IN,
    CHANGED;

    private final static IdentityHashMap<IElementType, Type> MAP = new IdentityHashMap<IElementType, Type>();
    static {
      MAP.put(JqlTokenTypes.EQ, EQ);
      MAP.put(JqlTokenTypes.NE, NE);
      MAP.put(JqlTokenTypes.LT, LT);
      MAP.put(JqlTokenTypes.GT, GT);
      MAP.put(JqlTokenTypes.LE, LE);
      MAP.put(JqlTokenTypes.GE, GE);
      MAP.put(JqlTokenTypes.CONTAINS, CONTAINS);
      MAP.put(JqlTokenTypes.NOT_CONTAINS, NOT_CONTAINS);
    }

    @Nullable
    public static Type fromTokenType(IElementType type) {
      return MAP.get(type);
    }

  }

  @Nullable
  Type getType();

  @NotNull
  JqlIdentifier getField();

  @NotNull
  String getFieldName();
}
