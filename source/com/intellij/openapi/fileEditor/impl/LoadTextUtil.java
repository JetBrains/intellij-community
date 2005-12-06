package com.intellij.openapi.fileEditor.impl;

import com.intellij.Patches;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetSettings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.SmartEncodingInputStream;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.CharArrayCharSequence;

import java.io.*;
import java.nio.charset.Charset;

public final class LoadTextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.LoadTextUtil");

  private static char[] ourSharedBuffer = new char[50000];

  public static Pair<CharSequence,String> loadText(byte[] bytes, final VirtualFile virtualFile) {
    try {
      return loadText(getReader(virtualFile, new ByteArrayInputStream(bytes)), bytes.length);
    } catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  private static Pair<CharSequence,String> loadText(Reader reader, int fileLength) throws IOException {
    char[] buffer = ourSharedBuffer.length >= fileLength ? ourSharedBuffer : new char[fileLength];

    int offset = 0;
    int read;
    do {
      read = reader.read( buffer, offset, buffer.length - offset);
      if (read < 0) break;
      offset += read;

      if (offset >= buffer.length) {
        // Number of characters read might exceed fileLength if the encoding being used is capable to
        // produce more than one character from a single byte. Need to reallocate in this case.
        char[] newBuffer = new char[buffer.length * 2];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        buffer = newBuffer;
      }
    } while(true);

    final int LF = 1;
    final int CR = 2;
    int line_separator = 0;

    int dst = 0;
    char prev = ' ';
    for( int src = 0; src < offset; src++ ) {
      char c = buffer[src];
      switch( c ) {
        case '\r':
          buffer[dst++] = '\n';
          line_separator = CR;
          break;
        case '\n':
          if( prev != '\r' ) {
            buffer[dst++] = '\n';
            line_separator = LF;
          }
          else line_separator = CR + LF;
          break;
        default:
          buffer[dst++] = c;
          break;
      }
      prev = c;
    }

    String detectedLineSeparator = null;
    switch( line_separator ) {
      case CR: detectedLineSeparator = "\r"; break;
      case LF: detectedLineSeparator = "\n"; break;
      case CR + LF: detectedLineSeparator = "\r\n"; break;
    }

    char chars[] = new char[dst];
    System.arraycopy(buffer, 0, chars, 0, chars.length);
    return new Pair<CharSequence, String>(new CharArrayCharSequence(chars), detectedLineSeparator);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Reader getReader(final VirtualFile virtualFile, InputStream stream) throws IOException {
    final Reader reader;

    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
    String charsetName = fileType.getCharset(virtualFile);
    if (charsetName != null) {
      virtualFile.setCharset(Charset.forName(charsetName));
      reader = new BufferedReader(new InputStreamReader(stream, virtualFile.getCharset()));
      skipUTF8BOM(virtualFile, reader);
      return reader;
    }

    CharsetSettings settings = CharsetSettings.getInstance();
    if (settings != null && settings.isUseUTFGuessing()) {
      SmartEncodingInputStream seis = new SmartEncodingInputStream(stream,
                                                                   SmartEncodingInputStream.BUFFER_LENGTH_4KB,
                                                                   CharsetToolkit.getIDEOptionsCharset(),
                                                                   true);
      virtualFile.setCharset(seis.getEncoding());
      reader = seis.getReader();
      //noinspection ConstantConditions
      if (Patches.SUN_BUG_ID_4508058) {
        virtualFile.setBOM(seis.detectUTF8_BOM());
      }
    }
    else {
      virtualFile.setCharset(CharsetToolkit.getIDEOptionsCharset());
      if (virtualFile.getCharset() != null) {
        reader = new BufferedReader(new InputStreamReader(stream, virtualFile.getCharset()));
        skipUTF8BOM(virtualFile, reader);
      }
      else {
        reader = new BufferedReader(new InputStreamReader(stream));
      }
    }

    return reader;
  }

  private static void skipUTF8BOM(final VirtualFile virtualFile, final Reader reader) throws IOException {
    //noinspection ConstantConditions
    if (Patches.SUN_BUG_ID_4508058) {
      //noinspection HardCodedStringLiteral
      if (virtualFile.getCharset() != null && virtualFile.getCharset().name().contains("UTF-8")) {
        reader.mark(1);
        char c = (char)reader.read();
        if (c == '\uFEFF') {
          virtualFile.setBOM(CharsetToolkit.UTF8_BOM);
        }
        else {
          reader.reset();
        }
      }
    }
  }

  /**
   * Gets the <code>Writer</code> for this file and sets modification stamp and time stamp to the specified values
   * after closing the Writer.<p>
   *
   * Normally you should not use this method.
   *
   * @param virtualFile
     * @param requestor any object to control who called this method. Note that
   * it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   * See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param newModificationStamp new modification stamp or -1 if no special value should be set
   * @param newTimeStamp new time stamp or -1 if no special value should be set
   * @return <code>Writer</code>
   * @throws java.io.IOException if an I/O error occurs
   * @see #getModificationStamp()
   */
  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Writer getWriter(final VirtualFile virtualFile, Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException{
    Charset charset = virtualFile.getCharset();
    OutputStream outputStream = virtualFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
    return new BufferedWriter(charset == null ? new OutputStreamWriter(outputStream) : new OutputStreamWriter(outputStream, charset));
  }
}
