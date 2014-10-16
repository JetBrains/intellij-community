/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.util.BitSet;
import java.util.List;

/**
 * @author yole
 */
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
    for (FutureFeature fuf : FutureFeature.ALL) {
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
  public IStubFileElementType getType() {
    return (IStubFileElementType) LanguageParserDefinitions.INSTANCE.forLanguage(PythonLanguage.getInstance()).getFileNodeType();
  }
}
