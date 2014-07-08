package com.intellij.tokenindex;

import com.intellij.util.containers.HashSet;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

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

  public void save(@NotNull DataOutput out, TokenIndexKey value) throws IOException {
    Set<String> languages = value.getLanguages();
    out.writeInt(languages.size());
    for (String language : languages) {
      out.writeUTF(language);
    }
    out.writeInt(value.getBlockId());
  }

  public TokenIndexKey read(@NotNull DataInput in) throws IOException {
    int languagesCount = in.readInt();
    Set<String> languages = new HashSet<String>();
    for (int i = 0; i < languagesCount; i++) {
      String languageId = in.readUTF();
      languages.add(languageId);
    }
    int blockId = in.readInt();
    return new TokenIndexKey(languages, blockId);
  }
}
