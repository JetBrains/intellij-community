package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.CharArrayCharSequence;

import java.io.IOException;
import java.io.Reader;

public final class LoadTextUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.LoadTextUtil");

  private static final char[] EMPTY_CHAR_ARRAY = new char[0];

  private static char[] ourSharedBuffer = new char[50000];
  private static final Object OUR_SHARED_BUFFER_LOCK = new Object();

  public static CharSequence loadText(VirtualFile file, String[] detectedLineSeparator){
    CharSequence chars = new CharArrayCharSequence(EMPTY_CHAR_ARRAY);
    if (!file.isDirectory()) {
      synchronized (OUR_SHARED_BUFFER_LOCK) {
        Reader reader = null;
        try {
          reader = file.getReader();
          int fileLength = (int)file.getLength();
          chars = loadText(reader, fileLength, detectedLineSeparator);
        }
        catch (IOException e) {
        }
        finally {
          if (reader != null) {
            try {
              reader.close();
            }
            catch (IOException e) {
            }
          }
        }
      }
    }
    return chars;
  }

  public static CharSequence loadText(byte[] bytes, String[] detectedLineSeparator) {
    try {
      return loadText(VirtualFile.getReader(bytes), bytes.length, detectedLineSeparator);
    } catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  private static CharSequence loadText(Reader reader, int fileLength, String[] detectedLineSeparator) throws IOException {

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

    if( detectedLineSeparator != null && detectedLineSeparator[0] == null ) {
      switch( line_separator ) {
        case CR: detectedLineSeparator[0] = "\r"; break;
        case LF: detectedLineSeparator[0] = "\n"; break;
        case CR + LF: detectedLineSeparator[0] = "\r\n"; break;
      }
    }

    char chars[] = new char[dst];
    System.arraycopy(buffer, 0, chars, 0, chars.length);
    return new CharArrayCharSequence(chars);
  }
}
