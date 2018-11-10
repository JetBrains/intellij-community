/*
 * Copyright (c) 2005, Joe Desbonnet, (jdesbonnet@gmail.com)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <copyright holder> ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <copyright holder> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ie.wombat.jbdiff;

import com.intellij.updater.Utils;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Java Binary Patch utility. Based on <a href="http://www.daemonology.net/bsdiff/">bsdiff (v4.2)</a> by Colin Percival.
 *
 * @author <a href="maito:jdesbonnet@gmail.com">Joe Desbonnet</a>
 */
public class JBPatch {
  private static final int BLOCK_SIZE = 2 * 1024 * 1024;

  public static void bspatch(InputStream oldFileIn, OutputStream newFileOut, InputStream diffFileIn) throws IOException {
    int oldPos, newPos;

    byte[] diffData = Utils.readBytes(diffFileIn);

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataInputStream diffIn = new DataInputStream(new ByteArrayInputStream(diffData));

    // ctrlBlockLen after gzip compression at heater offset 0 (length 8 bytes)
    long ctrlBlockLen = diffIn.readLong();

    // diffBlockLen after gzip compression at header offset 8 (length 8 bytes)
    long diffBlockLen = diffIn.readLong();

    // size of new file at header offset 16 (length 8 bytes)
    int newSize = (int)diffIn.readLong();

    InputStream in;
    in = new ByteArrayInputStream(diffData);
    skip(in, ctrlBlockLen + 24);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") GZIPInputStream diffBlockIn = new GZIPInputStream(in);

    in = new ByteArrayInputStream(diffData);
    skip(in, diffBlockLen + ctrlBlockLen + 24);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") GZIPInputStream extraBlockIn = new GZIPInputStream(in);

    byte[] oldBuf = realAllFileContent(oldFileIn);
    int oldSize = oldBuf.length;

    byte[] newBuf = new byte[BLOCK_SIZE];

    oldPos = 0;
    newPos = 0;

    while (newPos < newSize) {
      int bytesToReadFromDiffAndOld = diffIn.readInt();
      int bytesToReadFromExtraBlockIn = diffIn.readInt();
      int bytesToSkipFromOld = diffIn.readInt();

      if (newPos + bytesToReadFromDiffAndOld > newSize) {
        throw new IOException("Corrupted patch");
      }

      int nBytes = 0;
      while (nBytes < bytesToReadFromDiffAndOld) {
        int nBytesFromDiff = diffBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromDiffAndOld - nBytes));
        if (nBytesFromDiff < 0) {
          throw new IOException("error reading from diffBlockIn");
        }

        int nBytesFromOld = Math.min(nBytesFromDiff, oldSize - oldPos);
        for (int i = 0; i < nBytesFromOld; ++i) {
          newBuf[i] += oldBuf[oldPos + i];
        }

        nBytes += nBytesFromDiff;
        newPos += nBytesFromDiff;
        oldPos += nBytesFromDiff;
        Utils.writeBytes(newBuf, nBytesFromDiff, newFileOut);
      }

      if (bytesToReadFromExtraBlockIn > 0) {
        if (newPos + bytesToReadFromExtraBlockIn > newSize) {
          throw new IOException("Corrupted patch");
        }

        nBytes = 0;
        while (nBytes < bytesToReadFromExtraBlockIn) {
          int nBytesFromExtraBlockIn = extraBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromExtraBlockIn - nBytes));
          if (nBytesFromExtraBlockIn < 0) {
            throw new IOException("error reading from extraBlockIn");
          }

          nBytes += nBytesFromExtraBlockIn;
          newPos += nBytesFromExtraBlockIn;
          Utils.writeBytes(newBuf, nBytesFromExtraBlockIn, newFileOut);
        }
      }

      oldPos += bytesToSkipFromOld;
    }

    // TODO: Check if at end of ctrlIn
    // TODO: Check if at the end of diffIn
    // TODO: Check if at the end of extraIn

    diffBlockIn.close();
    extraBlockIn.close();
    diffIn.close();
  }

  private static void skip(InputStream in, long n) throws IOException {
    if (in.skip(n) != n) throw new IOException("Premature EOF?");
  }

  private static byte[] realAllFileContent(InputStream oldFileIn) throws IOException {
    // Important: oldFileIn may be very large: so use max size (to avoid allocating memory to next power of 2) +
    // do not hold reference for oldFileByteOut on stack
    ByteArrayOutputStream oldFileByteOut = new ByteArrayOutputStream(Math.max(oldFileIn.available(), 32));
    try {
      Utils.copyStream(oldFileIn, oldFileByteOut);
    }
    finally {
      oldFileByteOut.close();
    }
    return oldFileByteOut.toByteArray();
  }
}