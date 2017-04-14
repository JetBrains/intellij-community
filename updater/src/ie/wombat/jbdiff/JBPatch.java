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
 * Java Binary patcher (based on bspatch by Colin Percival)
 *
 * @author Joe Desbonnet, jdesbonnet@gmail.com
 */
public class JBPatch {
  private static final int block_size = 2 * 1024 * 1024;

  public static void bspatch(InputStream oldFileIn, OutputStream newFileOut, InputStream diffFileIn)
    throws IOException {

    int oldpos, newpos;

    byte[] diffData = Utils.readBytes(diffFileIn);

    DataInputStream diffIn = new DataInputStream(new ByteArrayInputStream(diffData));

    // headerMagic at header offset 0 (length 8 bytes)
    long headerMagic = diffIn.readLong();

    // ctrlBlockLen after gzip compression at heater offset 8 (length 8 bytes)
    long ctrlBlockLen = diffIn.readLong();

    // diffBlockLen after gzip compression at header offset 16 (length 8 bytes)
    long diffBlockLen = diffIn.readLong();

    // size of new file at header offset 24 (length 8 bytes)
    int newsize = (int)diffIn.readLong();

    InputStream in;
    in = new ByteArrayInputStream(diffData);
    in.skip(ctrlBlockLen + 32);
    GZIPInputStream diffBlockIn = new GZIPInputStream(in);

    in = new ByteArrayInputStream(diffData);
    in.skip(diffBlockLen + ctrlBlockLen + 32);
    GZIPInputStream extraBlockIn = new GZIPInputStream(in);

    final byte[] oldBuf = realAllFileContent(oldFileIn);
    final int oldsize = oldBuf.length;

    final byte[] newBuf = new byte[block_size];

    oldpos = 0;
    newpos = 0;

    while (newpos < newsize) {
      final int bytesToReadFromDiffAndOld = diffIn.readInt();
      final int bytesToReadFromExtraBlockIn = diffIn.readInt();
      final int bytesToSkipFromOld = diffIn.readInt();

      if (newpos + bytesToReadFromDiffAndOld > newsize) {
        System.err.println("Corrupted patch\n");
        return;
      }

      int nbytes = 0;
      while (nbytes < bytesToReadFromDiffAndOld) {
        int nBytesFromDiff = diffBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromDiffAndOld - nbytes));
        if (nBytesFromDiff < 0) {
          System.err.println("error reading from diffBlockIn");
          return;
        }

        int nbytesFromOld = Math.min(nBytesFromDiff, oldsize - oldpos);
        for (int i = 0; i < nbytesFromOld; ++i) {
          newBuf[i] += oldBuf[oldpos + i];
        }

        nbytes += nBytesFromDiff;
        newpos += nBytesFromDiff;
        oldpos += nBytesFromDiff;
        Utils.writeBytes(newBuf, nBytesFromDiff, newFileOut);
      }

      if (bytesToReadFromExtraBlockIn > 0) {
        if (newpos + bytesToReadFromExtraBlockIn > newsize) {
          System.err.println("Corrupted patch");
          return;
        }

        nbytes = 0;
        while (nbytes < bytesToReadFromExtraBlockIn) {
          int nBytesFromExtraBlockIn = extraBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromExtraBlockIn - nbytes));
          if (nBytesFromExtraBlockIn < 0) {
            System.err.println("error reading from extraBlockIn");
            return;
          }

          nbytes += nBytesFromExtraBlockIn;
          newpos += nBytesFromExtraBlockIn;
          Utils.writeBytes(newBuf, nBytesFromExtraBlockIn, newFileOut);
        }
      }

      oldpos += bytesToSkipFromOld;
    }

    // TODO: Check if at end of ctrlIn
    // TODO: Check if at the end of diffIn
    // TODO: Check if at the end of extraIn

    diffBlockIn.close();
    extraBlockIn.close();
    diffIn.close();
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