package com.intellij.openapi.fileEditor.impl;

import com.intellij.Patches;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetSettings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.SmartEncodingInputStream;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.xml.util.XmlUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

public final class LoadTextUtil {
  static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  private LoadTextUtil() {}

  private static Pair<CharSequence, String> convertLineSeparators(final CharBuffer buffer) {
    final int LF = 1;
    final int CR = 2;
    int line_separator = 0;

    int dst = 0;
    char prev = ' ';
    final int length = buffer.length();
    for (int src = 0; src < length; src++) {
      char c = buffer.charAt(src);
      switch (c) {
        case '\r':
          buffer.put(dst++, '\n');
          line_separator = CR;
          break;
        case '\n':
          if (prev == '\r') {
            line_separator = CR + LF;
          }
          else {
            buffer.put(dst++, '\n');
            line_separator = LF;
          }
          break;
        default:
          buffer.put(dst++, c);
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

    CharSequence result;
    if (buffer.length() == dst) {
      result = buffer;
    }
    else {
      char[] chars = new char[dst];
      System.arraycopy(buffer.array(), 0, chars, 0, dst);
      result = new CharArrayCharSequence(chars);
    }
    return Pair.create(result, detectedLineSeparator);
  }

  private static int detectCharsetAndSkipBOM(final VirtualFile virtualFile, final byte[] content) {
    FileType fileType = virtualFile.getFileType();
    String charsetName = fileType.getCharset(virtualFile);
    if (charsetName != null) {
      Charset charset = null;
      try {
        charset = Charset.forName(charsetName);
      }
      catch (IllegalCharsetNameException e) {
      }
      catch(UnsupportedCharsetException e){
      }
      virtualFile.setCharset(charset);
      return skipBOM(virtualFile, content);
    }

    CharsetSettings settings = CharsetSettings.getInstance();
    if (settings != null && settings.isUseUTFGuessing()) {
      CharsetToolkit toolkit = new CharsetToolkit(content, CharsetToolkit.getIDEOptionsCharset());
      toolkit.setEnforce8Bit(true);
      Charset charset = toolkit.guessEncoding(SmartEncodingInputStream.BUFFER_LENGTH_4KB);

      virtualFile.setCharset(charset);
      return skipBOM(virtualFile, content);
    }
    else {
      virtualFile.setCharset(CharsetToolkit.getIDEOptionsCharset());
      return skipBOM(virtualFile, content);
    }
  }

  private static int skipBOM(final VirtualFile virtualFile, byte[] content) {
    if (Patches.SUN_BUG_ID_4508058) {
      //noinspection HardCodedStringLiteral
      if (virtualFile.getCharset() != null && virtualFile.getCharset().name().contains("UTF-8") && CharsetToolkit.hasUTF8Bom(content)) {
        virtualFile.setBOM(CharsetToolkit.UTF8_BOM);
        return CharsetToolkit.UTF8_BOM.length;
      }
    }
    if (CharsetToolkit.hasUTF16LEBom(content)) {
      virtualFile.setBOM(CharsetToolkit.UTF16LE_BOM);
      return CharsetToolkit.UTF16LE_BOM.length;
    }
    if (CharsetToolkit.hasUTF16BEBom(content)) {
      virtualFile.setBOM(CharsetToolkit.UTF16BE_BOM);
      return CharsetToolkit.UTF16BE_BOM.length;
    }
    return 0;
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
   * @param text
   * @param newModificationStamp new modification stamp or -1 if no special value should be set
   * @return <code>Writer</code>
   * @throws java.io.IOException if an I/O error occurs
   * @see VirtualFile#getModificationStamp()
   */
  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Writer getWriter(final VirtualFile virtualFile, Object requestor, final String text, final long newModificationStamp) throws IOException{
    Charset charset = getCharsetForWriting(virtualFile, text);
    OutputStream outputStream = virtualFile.getOutputStream(requestor, newModificationStamp, -1);
    return new BufferedWriter(charset == null ? new OutputStreamWriter(outputStream) : new OutputStreamWriter(outputStream, charset));
  }

  private static Charset getCharsetForWriting(final VirtualFile virtualFile, final String text) {
    FileType fileType = virtualFile.getFileType();
    Charset charset = null;
    if (fileType instanceof XmlLikeFileType) {
      String name = XmlUtil.extractXmlEncodingFromProlog(text);
      if (name != null) {
        try {
          charset = Charset.forName(name);
        }
        catch (IllegalCharsetNameException e) {
        }
        catch(UnsupportedCharsetException e){
        }
      }
    }
    if (charset == null) {
      charset = virtualFile.getCharset();
    }
    return charset;
  }

  public static CharSequence loadText(VirtualFile file) {
    assert !file.isDirectory() : file.getPresentableUrl() + "is directory";
    final FileType fileType = file.getFileType();

    if (fileType.equals(StdFileTypes.CLASS)){
      return new CharArrayCharSequence(decompile(file));
    }
    assert !fileType.isBinary() : file.getPresentableUrl() + "is binary";

    try {
      final byte[] bytes = file.contentsToByteArray();
      return getTextByBinaryPresentation(bytes, file);
    }
    catch (IOException e) {
      return ArrayUtil.EMPTY_CHAR_SEQUENCE;
    }
  }

  static char[] decompile(VirtualFile file) {
    //try {
      final ProjectEx dummyProject = ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).getDummyProject();
      PsiManager manager = PsiManager.getInstance(dummyProject);
      final String text = ClsFileImpl.decompile(manager, file);

      PsiFile mirror = manager.getElementFactory().createFileFromText("test.java", text);

      //CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(dummyProject); // do not use project's code style!
      //CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(dummyProject);
      //boolean saved = settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
      //settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
      //codeStyleManager.shortenClassReferences(mirror);
      //codeStyleManager.reformat(mirror);
      //settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = saved;

      return mirror.textToCharArray();
    //}
    //catch(IncorrectOperationException e){
    //  LOG.error(e);
    //  return null;
    //}
  }

  public static CharSequence getTextByBinaryPresentation(final byte[] bytes, final VirtualFile virtualFile) {
    int offset = detectCharsetAndSkipBOM(virtualFile, bytes);
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);

    Charset charset = virtualFile.getCharset();
    if (charset == null) {
      charset = CharsetToolkit.getDefaultSystemCharset();
    }
    if (charset == null) {
      //noinspection HardCodedStringLiteral
      charset = Charset.forName("ISO-8859-1");
    }
    CharBuffer charBuffer = charset.decode(byteBuffer);
    Pair<CharSequence, String> result = convertLineSeparators(charBuffer);
    virtualFile.putUserData(DETECTED_LINE_SEPARATOR_KEY, result.getSecond());
    return result.getFirst();
  }
}
