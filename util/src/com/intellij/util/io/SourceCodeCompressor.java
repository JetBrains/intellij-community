/*
 * @author max
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class SourceCodeCompressor {
  private final static VaultOutputStream OUTPUT;
  private final static byte[] PRESET_BUF;
  private final static Deflater DEFLATER;
  private final static Inflater INFLATER;
  private final static byte[] INFLATE_BUFFER;

  private SourceCodeCompressor() {
  }

  static {
    @NonNls final String preset_buf_string =
      "                   ;\r\n\r\n\r\n\r\n\n\n\n { {\r\n }\r\n = == != < > >= <= ? : ++ += -- -= [] [i] () ()) ())) (); ()); ())); () {" +
      "// /* /** */ * opyright (c)package com.import java.utilimport javax.swingimport java.awt" +
      "import com.intellijimport org.import gnu.*;new super(this(public interface extends implements " +
      "public abstract class public class private final static final protected synchronized my our " +
      "instanceof throws return return;if (else {for (while (do {break;continue;throw try {catch (finally {" +
      "null;true;false;void byte short int long boolean float double Object String Class System.Exception Throwable" +
      "getsetputcontainsrunashCodeequalslengthsizeremoveaddclearwritereadopenclosename=\"getNamerray" +
      "istollectionHashMapSetnpututputtreamhildrenarentrootitemctionefaultrojectomponentpplicationerializ" +
      "Created by IntelliJ IDEA.@author Logger ettingsFontialog JPanel JLabel JCheckBox JComboBox JList JSpinner " +
      "<html>/>\r\n<head</head><body bgcolor=</body>table<?xml version=\"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML" +
      "titleframecaret<a href=\"http://</a><div </div><td </td><tr </tr><p </p><hscripttext/css<img src=" +
      "<!--><link rel=width=height=align=span=centerrightleftstyle=celljsp:rootxmlns:avascript";
    PRESET_BUF = preset_buf_string.getBytes();
    OUTPUT = new VaultOutputStream();
    DEFLATER = new Deflater(Deflater.BEST_COMPRESSION);
    INFLATER = new Inflater();
    INFLATE_BUFFER = new byte[4096];
  }

  public static synchronized byte[] compress(byte[] source) {
    try {
      DEFLATER.reset();
      DEFLATER.setDictionary(PRESET_BUF);
      try {
        DeflaterOutputStream output = null;
        try {
          output = new DeflaterOutputStream(OUTPUT, DEFLATER);
          output.write(source);
        }
        finally {
          if (output != null) {
            output.close();
          }
        }
      }
      catch (IOException e) {
        return source;
      }
      return OUTPUT.toByteArray();
    }
    finally {
      OUTPUT.reset();
    }
  }

  public static synchronized byte[] decompress(byte[] compressed) throws IOException {
    INFLATER.reset();
    InflaterInputStream input = null;
    try {
      input = new InflaterInputStream(new ByteArrayInputStream(compressed), INFLATER);
      final int b = input.read();
      if (b == -1) {
        INFLATER.setDictionary(PRESET_BUF);
      }
      else {
        OUTPUT.write(b);
      }
      int readBytes;
      while ((readBytes = input.read(INFLATE_BUFFER)) > 0) {
        OUTPUT.write(INFLATE_BUFFER, 0, readBytes);
      }
      return OUTPUT.toByteArray();
    }
    finally {
      if (input != null) {
        input.close();
      }
      OUTPUT.reset();
    }
  }

  private static class VaultOutputStream extends ByteArrayOutputStream {

    private static final int MIN_BUF_SIZE = 0x10000;
    private byte[] MIN_BUFFER;

    public VaultOutputStream() {
      super(MIN_BUF_SIZE);
      MIN_BUFFER = buf;
    }

    @SuppressWarnings({"NonSynchronizedMethodOverridesSynchronizedMethod"})
    public void reset() {
      count = 0;
      buf = MIN_BUFFER;
    }
  }
}