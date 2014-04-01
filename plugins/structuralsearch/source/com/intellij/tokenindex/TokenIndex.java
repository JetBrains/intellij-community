package com.intellij.tokenindex;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenIndex extends FileBasedIndexExtension<TokenIndexKey, List<Token>> {
  private static final int FILE_BLOCK_SIZE = 100;

  public static final ID<TokenIndexKey, List<Token>> INDEX_ID = ID.create("token.index");

  private static final int VERSION = 3;

  private final KeyDescriptor<TokenIndexKey> myKeyDescriptor = new TokenIndexKeyDescriptor();

  private static final int ANONYM_TOKEN_ID = 0;
  private static final int TEXT_TOKEN_ID = 1;
  private static final int MARKER_TOKEN_ID = 2;
  private static final int INDENT_TOKEN_ID = 3;

  private final DataExternalizer<List<Token>> myDataExternalizer = new DataExternalizer<List<Token>>() {
    @Override
    public void save(@NotNull DataOutput out, List<Token> value) throws IOException {
      out.writeInt(value.size());
      for (Token token : value) {
        if (token instanceof AnonymToken) {
          out.writeByte(ANONYM_TOKEN_ID);
          out.writeInt(token.getStart());
          out.writeInt(token.getEnd());
          out.writeByte(((AnonymToken)token).getType());
        }
        else if (token instanceof TextToken) {
          out.writeByte(TEXT_TOKEN_ID);
          out.writeInt(token.getStart());
          out.writeInt(token.getEnd());
          out.writeInt(((TextToken)token).getHash());
        }
        else if (token instanceof PathMarkerToken) {
          out.writeByte(MARKER_TOKEN_ID);
          out.writeUTF(((PathMarkerToken)token).getPath());
        }
        else if (token instanceof IndentToken) {
          out.writeByte(INDENT_TOKEN_ID);
          out.writeInt(token.getStart());
          out.writeInt(token.getEnd());
        }
        else {
          assert false : "Unsupported token type " + token.getClass();
        }
      }
    }

    @Override
    public List<Token> read(@NotNull DataInput in) throws IOException {
      List<Token> result = new ArrayList<Token>();
      int n = in.readInt();
      for (int i = 0; i < n; i++) {
        byte tokenTypeId = in.readByte();
        switch (tokenTypeId) {
          case ANONYM_TOKEN_ID: {
            int start = in.readInt();
            int end = in.readInt();
            byte anonymTokenTypeValue = in.readByte();
            result.add(new AnonymToken(anonymTokenTypeValue, start, end));
            break;
          }
          case TEXT_TOKEN_ID: {
            int start = in.readInt();
            int end = in.readInt();
            int hash = in.readInt();
            result.add(new TextToken(hash, start, end));
            break;
          }
          case MARKER_TOKEN_ID: {
            String path = in.readUTF();
            result.add(new PathMarkerToken(path));
            break;
          }
          case INDENT_TOKEN_ID:
            int start = in.readInt();
            int end = in.readInt();
            result.add(new IndentToken(start, end));
            break;
        }
      }
      return result;
    }
  };

  @NotNull
  @Override
  public ID<TokenIndexKey, List<Token>> getName() {
    return INDEX_ID;
  }

  private static int getBlockId(String filePath) {
    int h = filePath.hashCode();
    if (h < 0) {
      h = -h;
    }
    return h % FILE_BLOCK_SIZE;
  }

  @NotNull
  @Override
  public DataIndexer<TokenIndexKey, List<Token>, FileContent> getIndexer() {
    return new DataIndexer<TokenIndexKey, List<Token>, FileContent>() {
      @Override
      @NotNull
      public Map<TokenIndexKey, List<Token>> map(@NotNull FileContent inputData) {
        if (true) return Collections.EMPTY_MAP; // TODO: Eugene index is VERY unefficient and leads to OME
        Map<TokenIndexKey, List<Token>> result = new HashMap<TokenIndexKey, List<Token>>(1);
        RecursiveTokenizingVisitor visitor = new RecursiveTokenizingVisitor();
        inputData.getPsiFile().accept(visitor);
        List<Token> tokens = visitor.getTokens();
        if (tokens.size() > 0) {
          String path = inputData.getFile().getPath();
          tokens.add(new PathMarkerToken(path));
          TokenIndexKey key = new TokenIndexKey(visitor.getLanguages(), getBlockId(path));
          result.put(key, tokens);
        }
        return result;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<TokenIndexKey> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public DataExternalizer<List<Token>> getValueExternalizer() {
    return myDataExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.InputFilter() {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        if (file.getFileSystem() instanceof JarFileSystem) return false;
        return file.getFileType() instanceof LanguageFileType;
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public int getCacheSize() {
    return 1;
  }

  public static boolean supports(Language language) {
    return StructuralSearchUtil.getTokenizerForLanguage(language) != null;
  }
}
