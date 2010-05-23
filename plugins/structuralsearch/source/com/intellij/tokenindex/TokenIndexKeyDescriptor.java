package com.intellij.tokenindex;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenIndexKeyDescriptor implements KeyDescriptor<TokenIndexKey> {
  public int getHashCode(TokenIndexKey value) {
    return value.hashCode();
  }

  public boolean isEqual(TokenIndexKey val1, TokenIndexKey val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, TokenIndexKey value) throws IOException {
    out.writeUTF(value.getLanguageId());
    out.writeInt(value.getBlockId());
  }

  public TokenIndexKey read(DataInput in) throws IOException {
    String languageId = in.readUTF();
    int blockId = in.readInt();
    return new TokenIndexKey(languageId, blockId);
  }
}
