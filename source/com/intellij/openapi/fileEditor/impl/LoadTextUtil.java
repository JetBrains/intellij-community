package com.intellij.openapi.fileEditor.impl;

import com.intellij.Patches;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetSettings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public final class LoadTextUtil {
  static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");

  private LoadTextUtil() {
  }

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
    switch (line_separator) {
      case CR:
        detectedLineSeparator = "\r";
        break;
      case LF:
        detectedLineSeparator = "\n";
        break;
      case CR + LF:
        detectedLineSeparator = "\r\n";
        break;
    }

    CharSequence result;
    if (buffer.length() == dst) {
      result = buffer;
    }
    else {
      result = buffer.subSequence(0, dst);
    }
    return Pair.create(result, detectedLineSeparator);
  }

  public static void detectCharset(final VirtualFile virtualFile, final byte[] content) {
    Charset charset = dodetectCharset(virtualFile, content);
    virtualFile.setCharset(charset == null ? CharsetToolkit.getIDEOptionsCharset() : charset);
  }

  private static Charset dodetectCharset(final VirtualFile virtualFile, final byte[] content) {
    //Charset saved = EncodingManager.getInstance().getEncoding(virtualFile, true);
    //if (saved != null) return saved;

    CharsetSettings settings = CharsetSettings.getInstance();
    boolean shouldGuess = settings != null && settings.isUseUTFGuessing();
    CharsetToolkit toolkit = shouldGuess ? new CharsetToolkit(content, CharsetToolkit.getIDEOptionsCharset()) : null;
    if (shouldGuess) {
      toolkit.setEnforce8Bit(true);
      Charset charset = toolkit.guessFromBOM();
      if (charset != null) return charset;
      CharsetToolkit.GuessedEncoding guessed = toolkit.guessFromContent(content.length);
      if (guessed == CharsetToolkit.GuessedEncoding.VALID_UTF8) return CharsetToolkit.UTF8_CHARSET; //UTF detected, ignore all directives
    }

    FileType fileType = virtualFile.getFileType();
    String charsetName = fileType.getCharset(virtualFile);
    return CharsetToolkit.forName(charsetName);
  }

  private static int skipBOM(final VirtualFile virtualFile, byte[] content) {
    if (Patches.SUN_BUG_ID_4508058) {
      if (virtualFile.getCharset() != null && virtualFile.getCharset().name().contains(CharsetToolkit.UTF8) &&
          CharsetToolkit.hasUTF8Bom(content)) {
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
   * <p/>
   * Normally you should not use this method.
   *
   * @param project
   *@param virtualFile
   * @param requestor            any object to control who called this method. Note that
 *                             it is considered to be an external change if <code>requestor</code> is <code>null</code>.
 *                             See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param text
   * @param newModificationStamp new modification stamp or -1 if no special value should be set @return <code>Writer</code>
   * @throws java.io.IOException if an I/O error occurs
   * @see VirtualFile#getModificationStamp()
   */
  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public static Writer getWriter(@Nullable Project project, final VirtualFile virtualFile, Object requestor, final String text, final long newModificationStamp)
    throws IOException {
    Charset existing = virtualFile.getCharset();
    Charset specified = extractCharsetFromFileContent(project, virtualFile, text);
    Charset charset = chooseMostlyHarmlessCharset(existing, specified, text);
    if (charset != null) {
      virtualFile.setCharset(charset);
    }
    OutputStream outputStream = virtualFile.getOutputStream(requestor, newModificationStamp, -1);
    return new BufferedWriter(specified == null ? new OutputStreamWriter(outputStream) : new OutputStreamWriter(outputStream, charset));
  }

  private static Charset chooseMostlyHarmlessCharset(Charset existing, Charset specified, String text) {
    if (existing == null) return specified;
    if (specified == null) return existing;
    if (specified.equals(existing)) return specified;
    boolean isExistingLossy = false;
    boolean isSpecifiedLossy = false;
    for (int i=0; i<text.length();i++) {
      char c = text.charAt(i);
      String str = Character.toString(c);
      isExistingLossy |= !isSupported(existing, str);
      isSpecifiedLossy |= !isSupported(specified, str);
    }
    if (!isSpecifiedLossy) return specified; //if explicitly specified encoding is safe, return it
    if (!isExistingLossy) return existing;   //otherwise stick to the old encoding if it's ok
    return specified;                        //if both are bad it's no difference
  }

  private static boolean isSupported(Charset charset, String str) {
    ByteBuffer out = charset.encode(str);
    CharBuffer buffer = charset.decode(out);
    return str.equals(buffer.toString());
  }

  public static Charset extractCharsetFromFileContent(@Nullable Project project, final VirtualFile virtualFile, final String text) {
    FileType fileType = virtualFile.getFileType();
    Charset charset = null;
    if (fileType instanceof LanguageFileType) {
      charset = ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    if (charset == null) charset = virtualFile.getCharset();
    return charset;
  }

  public static CharSequence loadText(VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      return ((LightVirtualFile)file).getContent();
    }

    assert !file.isDirectory() : file.getPresentableUrl() + "is directory";
    final FileType fileType = file.getFileType();

    if (fileType.equals(StdFileTypes.CLASS)) {
      return decompile(file);
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

  private static CharSequence decompile(VirtualFile file) {
    final Project project;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      project = ((ProjectManagerEx)ProjectManager.getInstance()).getCurrentTestProject();
      assert project != null;
    }
    else {
      final Project[] projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) return "";
      project = projects[0];
    }

    return ClsFileImpl.decompile(PsiManager.getInstance(project), file);
  }

  public static CharSequence getTextByBinaryPresentation(final byte[] bytes, final VirtualFile virtualFile) {
    return getTextByBinaryPresentation(bytes, virtualFile, true);
  }

  public static CharSequence getTextByBinaryPresentation(final byte[] bytes,
                                                         final VirtualFile virtualFile,
                                                         final boolean rememberDetectedSeparators) {
    detectCharset(virtualFile, bytes);
    int offset = skipBOM(virtualFile, bytes);
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
    if (rememberDetectedSeparators) {
      virtualFile.putUserData(DETECTED_LINE_SEPARATOR_KEY, result.getSecond());
    }
    return result.getFirst();
  }
}
