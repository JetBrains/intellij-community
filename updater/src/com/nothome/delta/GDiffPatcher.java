/*
 *
 * Copyright (c) 2001 Torgeir Veimo
 * Copyright (c) 2006 Heiko Klein
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

import java.io.*;
import java.nio.ByteBuffer;

import static com.nothome.delta.GDiffWriter.*;

/**
 * This class patches an input file with a GDIFF patch file.
 *
 * The patch file follows the GDIFF file specification available at
 * <a href="http://www.w3.org/TR/NOTE-gdiff-19970901.html">http://www.w3.org/TR/NOTE-gdiff-19970901.html</a>.
 */
public class GDiffPatcher {

  private ByteBuffer buf = ByteBuffer.allocate(1024);
  private byte[] buf2 = buf.array();

  /**
   * Constructs a new GDiffPatcher.
   */
  public GDiffPatcher() {
  }

  /**
   * Patches to an output stream.
   */
  public void patch(File input, InputStream patch, OutputStream out) throws IOException {
    SeekableSource source = new RandomAccessFileSeekableSource(new RandomAccessFile(input, "r"));
    DataOutputStream outOS = new DataOutputStream(out);
    DataInputStream patchIS = new DataInputStream(patch);

    // the magic string is 'd1 ff d1 ff' + the version number
    //noinspection DuplicateCondition
    if (patchIS.readUnsignedByte() != 0xd1 ||
        patchIS.readUnsignedByte() != 0xff ||
        patchIS.readUnsignedByte() != 0xd1 ||
        patchIS.readUnsignedByte() != 0xff ||
        patchIS.readUnsignedByte() != 0x04) {

      throw new IOException("magic string not found, aborting!");
    }

    while (true) {
      int command = patchIS.readUnsignedByte();
      if (command == EOF) {
        break;
      }
      int length;
      int offset;

      if (command <= DATA_MAX) {
        append(command, patchIS, outOS);
        continue;
      }

      switch (command) {
        case DATA_USHORT: // ushort, n bytes following; append
          length = patchIS.readUnsignedShort();
          append(length, patchIS, outOS);
          break;
        case DATA_INT: // int, n bytes following; append
          length = patchIS.readInt();
          append(length, patchIS, outOS);
          break;
        case COPY_USHORT_UBYTE:
          offset = patchIS.readUnsignedShort();
          length = patchIS.readUnsignedByte();
          copy(offset, length, source, outOS);
          break;
        case COPY_USHORT_USHORT:
          offset = patchIS.readUnsignedShort();
          length = patchIS.readUnsignedShort();
          copy(offset, length, source, outOS);
          break;
        case COPY_USHORT_INT:
          offset = patchIS.readUnsignedShort();
          length = patchIS.readInt();
          copy(offset, length, source, outOS);
          break;
        case COPY_INT_UBYTE:
          offset = patchIS.readInt();
          length = patchIS.readUnsignedByte();
          copy(offset, length, source, outOS);
          break;
        case COPY_INT_USHORT:
          offset = patchIS.readInt();
          length = patchIS.readUnsignedShort();
          copy(offset, length, source, outOS);
          break;
        case COPY_INT_INT:
          offset = patchIS.readInt();
          length = patchIS.readInt();
          copy(offset, length, source, outOS);
          break;
        case COPY_LONG_INT:
          long loffset = patchIS.readLong();
          length = patchIS.readInt();
          copy(loffset, length, source, outOS);
          break;
        default:
          throw new IllegalStateException("command " + command);
      }
    }
    outOS.flush();
  }

  private void copy(long offset, int length, SeekableSource source, OutputStream output)
    throws IOException {
    source.seek(offset);
    while (length > 0) {
      int len = Math.min(buf.capacity(), length);
      buf.clear().limit(len);
      int res = source.read(buf);
      if (res == -1) {
        throw new EOFException("in copy " + offset + " " + length);
      }
      output.write(buf.array(), 0, res);
      length -= res;
    }
  }

  private void append(int length, InputStream patch, OutputStream output) throws IOException {
    while (length > 0) {
      int len = Math.min(buf2.length, length);
      int res = patch.read(buf2, 0, len);
      if (res == -1) {
        throw new EOFException("cannot read " + length);
      }
      output.write(buf2, 0, res);
      length -= res;
    }
  }
}

