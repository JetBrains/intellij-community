/* 
 *
 * Copyright (c) 2001 Torgeir Veimo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */

package com.nothome.delta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Outputs a diff following the GDIFF file specification available at
 * http://www.w3.org/TR/NOTE-gdiff-19970901.html.
 */
public class GDiffWriter {

  /**
   * Max length of a chunk.
   */
  public static final int CHUNK_SIZE = Short.MAX_VALUE;

  public static final byte EOF = 0;

  /**
   * Max length for single length data encode.
   */
  public static final int DATA_MAX = 246;

  public static final int DATA_USHORT = 247;
  public static final int DATA_INT = 248;
  public static final int COPY_USHORT_UBYTE = 249;
  public static final int COPY_USHORT_USHORT = 250;
  public static final int COPY_USHORT_INT = 251;
  public static final int COPY_INT_UBYTE = 252;
  public static final int COPY_INT_USHORT = 253;
  public static final int COPY_INT_INT = 254;
  public static final int COPY_LONG_INT = 255;

  private ByteArrayOutputStream buf = new ByteArrayOutputStream();

  private DataOutputStream output = null;

  /**
   * Constructs a new GDiffWriter.
   */
  public GDiffWriter(DataOutputStream os) throws IOException {
    this.output = os;
    // write magic string "d1 ff d1 ff 04"
    output.writeByte(0xd1);
    output.writeByte(0xff);
    output.writeByte(0xd1);
    output.writeByte(0xff);
    output.writeByte(0x04);
  }

  /**
   * Constructs a new GDiffWriter.
   */
  public GDiffWriter(OutputStream output) throws IOException {
    this(new DataOutputStream(output));
  }

  public void addCopy(long offset, int length) throws IOException {
    writeBuf();

    // output real data
    if (offset > Integer.MAX_VALUE) {
      // Actually, we don't support longer files than int.MAX_VALUE at the moment..
      output.writeByte(COPY_LONG_INT);
      output.writeLong(offset);
      output.writeInt(length);
    }
    else if (offset < 65536) {
      if (length < 256) {
        output.writeByte(COPY_USHORT_UBYTE);
        output.writeShort((int)offset);
        output.writeByte(length);
      }
      else if (length > 65535) {
        output.writeByte(COPY_USHORT_INT);
        output.writeShort((int)offset);
        output.writeInt(length);
      }
      else {
        output.writeByte(COPY_USHORT_USHORT);
        output.writeShort((int)offset);
        output.writeShort(length);
      }
    }
    else {
      if (length < 256) {
        output.writeByte(COPY_INT_UBYTE);
        output.writeInt((int)offset);
        output.writeByte(length);
      }
      else if (length > 65535) {
        output.writeByte(COPY_INT_INT);
        output.writeInt((int)offset);
        output.writeInt(length);
      }
      else {
        output.writeByte(COPY_INT_USHORT);
        output.writeInt((int)offset);
        output.writeShort(length);
      }
    }
  }

  /**
   * Adds a data byte.
   */
  public void addData(byte b) throws IOException {
    buf.write(b);
    if (buf.size() >= CHUNK_SIZE) {
      writeBuf();
    }
  }

  private void writeBuf() throws IOException {
    if (buf.size() > 0) {
      if (buf.size() <= DATA_MAX) {
        output.writeByte(buf.size());
      }
      else if (buf.size() <= 65535) {
        output.writeByte(DATA_USHORT);
        output.writeShort(buf.size());
      }
      else {
        output.writeByte(DATA_INT);
        output.writeInt(buf.size());
      }
      buf.writeTo(output);
      buf.reset();
    }
  }

  /**
   * Flushes accumulated data bytes, if any.
   */
  public void flush() throws IOException {
    writeBuf();
    output.flush();
  }

  /**
   * Writes the final EOF byte
   */
  public void end() throws IOException {
    this.flush();
    output.write(EOF);
  }

}

