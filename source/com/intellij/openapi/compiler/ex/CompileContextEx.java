package com.intellij.openapi.compiler.ex;

import com.intellij.compiler.make.DependencyCache;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

public interface CompileContextEx extends CompileContext {
  DependencyCache getDependencyCache();

  VirtualFile getSourceFileByOutputFile(VirtualFile outputFile);

  void addMessage(CompilerMessage message);
}
