package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.CompilerDirectoryTimestamp");
  
  private static final Map<String, FileAttribute> ourAttributes = new HashMap<String, FileAttribute>();

  public static boolean isFileIndexed(VirtualFile file, String indexName) {
    try {
      if (!file.isValid()) {
        return false;
      }
      final DataInputStream stream = getAttribute(indexName).readAttribute(file);
      if (stream == null) {
        return false;
      }
      try {
        if (!stream.readBoolean()) {
          return false;
        }
      }
      finally {
        stream.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }

  public static void update(VirtualFile file, String indexName) {
    try {
      if (file.isValid()) {
        final DataOutputStream stream = getAttribute(indexName).writeAttribute(file);
        try {
          stream.writeBoolean(true);
        }
        finally {
          stream.close();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }
  
  @NotNull
  private static FileAttribute getAttribute(String indexName) {
    FileAttribute attrib = ourAttributes.get(indexName);
    if (attrib == null) {
      attrib = new FileAttribute("_indexing_stamp_" + indexName, 1);
      ourAttributes.put(indexName, attrib);
    }
    return attrib;
  }
  
}
