package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;

/**
 *  @author dsl
 */
public interface ClonableContentFolder {
  ContentFolder cloneFolder(ContentEntry contentEntry);
}
