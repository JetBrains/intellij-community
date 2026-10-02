// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;


@ApiStatus.Internal
public class PyFileStubImpl extends PsiFileStubImpl<PyFile> implements PyFileStub {
  private final List<String> myDunderAll;
  private final BitSet myFutureFeatures; // stores IDs of features
  private final StringRef myDeprecationMessage;

  private static final int FUTURE_FEATURE_SET_SIZE = 32; // 32 features is ought to be enough for everybody! all bits fit into an int.

  public PyFileStubImpl(final PyFile file) {
    super(file);
    final PyFileImpl fileImpl = (PyFileImpl)file;
    myFutureFeatures = new BitSet(FUTURE_FEATURE_SET_SIZE);
    myDunderAll = fileImpl.calculateDunderAll();
    for (FutureFeature fuf : FutureFeature.values()) {
      myFutureFeatures.set(fuf.ordinal(), fileImpl.calculateImportFromFuture(fuf));
    }
    String message = fileImpl.extractDeprecationMessage();
    myDeprecationMessage = message == null ? null : StringRef.fromString(message);
  }

  public PyFileStubImpl(List<String> dunderAll, final BitSet future_features, final StringRef deprecationMessage) {
    super(null);
    myDunderAll = dunderAll;
    myFutureFeatures = future_features;
    myDeprecationMessage = deprecationMessage;
  }

  @Override
  public List<String> getDunderAll() {
    return myDunderAll;
  }

  @Override
  public BitSet getFutureFeatures() {
    return myFutureFeatures;
  }

  @Override
  public String getDeprecationMessage() {
    return myDeprecationMessage == null ? null : myDeprecationMessage.getString();
  }

  @Override
  public @NotNull IStubFileElementType<?> getType() {
    return (IStubFileElementType<?>)LanguageParserDefinitions.INSTANCE.forLanguage(PythonLanguage.getInstance()).getFileNodeType();
  }
}
