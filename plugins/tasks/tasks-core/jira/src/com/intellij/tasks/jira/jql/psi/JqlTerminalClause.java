package com.intellij.tasks.jira.jql.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * @author Mikhail Golubev
 */
public interface JqlTerminalClause extends JqlClause {
  enum Type {
    EQ(false),
    NE(false),
    LT(false),
    GT(false),
    LE(false),
    GE(false),
    CONTAINS(false),
    NOT_CONTAINS(false),
    IS(false),
    IS_NOT(false),
    IN(true),
    NOT_IN(true),
    WAS(false),
    WAS_IN(true),
    WAS_NOT(false),
    WAS_NOT_IN(true),
    CHANGED(false);

    private boolean myListOperator;

    Type(boolean listOperator) {
      myListOperator = listOperator;
    }

    private final static IdentityHashMap<IElementType, Type> MAP = new IdentityHashMap<>();

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

    public boolean isListOperator() {
      return myListOperator;
    }
  }

  @Nullable
  Type getType();

  @NotNull
  JqlIdentifier getField();

  @NotNull
  String getFieldName();
}
