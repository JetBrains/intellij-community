package com.intellij.pom.tree.events;

import com.intellij.lang.ASTNode;

public interface ReplaceChangeInfo {
  ASTNode getReplaced();
}
