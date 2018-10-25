// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.navigation.YAMLKeysStubIndex;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;
import org.jetbrains.yaml.psi.stubs.YAMLKeyStub;
import org.jetbrains.yaml.psi.stubs.YAMLKeyStubImpl;

import java.io.IOException;

public class YAMLKeyValueStubElementType extends IStubElementType<YAMLKeyStub, YAMLKeyValue> {
  public static final TokenSet BAD_PARENT_TYPES = TokenSet.create(YAMLElementTypes.SEQUENCE, YAMLElementTypes.ARRAY);

  public YAMLKeyValueStubElementType(@NotNull String debugName) {
    super(debugName, YAMLLanguage.INSTANCE);
  }

  @Override
  public YAMLKeyValue createPsi(@NotNull YAMLKeyStub stub) {
    return new YAMLKeyValueImpl(stub);
  }

  @NotNull
  @Override
  public YAMLKeyStub createStub(@NotNull YAMLKeyValue kv, @Nullable StubElement parentStub) {
    return new YAMLKeyStubImpl(parentStub, this, kv.getKeyText(), kv.getConfigFullPath());
  }

  /** Keys under sequences will not be stored */
  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return TreeUtil.findParent(node, BAD_PARENT_TYPES) == null;
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "yaml." + super.toString();
  }

  @Override
  public void serialize(@NotNull YAMLKeyStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getKeyText());
    dataStream.writeName(stub.getKeyPath());
  }

  @NotNull
  @Override
  public YAMLKeyStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    StringRef path = dataStream.readName();
    assert name != null;
    assert path != null;
    return new YAMLKeyStubImpl(parentStub, this, name, path);
  }

  @Override
  public void indexStub(@NotNull YAMLKeyStub stub, @NotNull IndexSink sink) {
    String path = stub.getKeyPath();
    sink.occurrence(YAMLKeysStubIndex.KEY, path);
  }
}
