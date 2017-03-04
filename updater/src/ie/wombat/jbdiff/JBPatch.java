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

    int newpos;

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

    /*
                System.err.println ("newsize=" + newsize);
                System.err.println ("ctrlBlockLen=" + ctrlBlockLen);
                System.err.println ("diffBlockLen=" + diffBlockLen);
                System.err.println ("newsize=" + newsize);
                */

    InputStream in;
    in = new ByteArrayInputStream(diffData);
    in.skip(ctrlBlockLen + 32);
    GZIPInputStream diffBlockIn = new GZIPInputStream(in);

    in = new ByteArrayInputStream(diffData);
    in.skip(diffBlockLen + ctrlBlockLen + 32);
    GZIPInputStream extraBlockIn = new GZIPInputStream(in);

    byte[] oldBuf = new byte[block_size];
    byte[] newBuf = new byte[block_size];
    
    newpos = 0;

    while (newpos < newsize) {
      final int bytesToReadFromDiffAndOld = diffIn.readInt();
      final int bytesToReadFromExtraBlockIn = diffIn.readInt();
      final int bytesToSkipFromOld = diffIn.readInt();

      if (newpos + bytesToReadFromDiffAndOld > newsize) {
        System.err.println("Corrupted patch\n");
        return;
      }

      int totalBytesRead = 0;

      while (totalBytesRead < bytesToReadFromDiffAndOld) {
        int nBytesFromDiff = diffBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromDiffAndOld - totalBytesRead));
        if (nBytesFromDiff < 0) {
          System.err.println("error reading from diffBlockIn");
          return;
        }
        int nbytesFromOld = oldFileIn.read(oldBuf, 0, Math.min(oldBuf.length, bytesToReadFromDiffAndOld - totalBytesRead));
        if (nbytesFromOld < 0) {
          System.err.println ("oldFileIn read failed prematurely. Read " + totalBytesRead + " bytes");
          return;
        }

        for (int i = 0; i < nbytesFromOld; ++i) {
          newBuf[i] += oldBuf[i];
        }

        totalBytesRead+=nbytesFromOld;
        newpos += nbytesFromOld;
        newFileOut.write(newBuf, 0, nbytesFromOld);
      }

      if (bytesToReadFromExtraBlockIn > 0) {
        if (newpos + bytesToReadFromExtraBlockIn > newsize) {
          System.err.println("Corrupted patch");
          return;
        }

        totalBytesRead = 0;
        while (totalBytesRead < bytesToReadFromExtraBlockIn) {
          int nBytesFromExtraBlockIn = extraBlockIn.read(newBuf, 0, Math.min(newBuf.length, bytesToReadFromExtraBlockIn - totalBytesRead));
          if (nBytesFromExtraBlockIn < 0) {
            System.err.println("error reading from extraBlockIn");
            return;
          }

          totalBytesRead += nBytesFromExtraBlockIn;
          newpos += nBytesFromExtraBlockIn;
          newFileOut.write(newBuf, 0, nBytesFromExtraBlockIn);
        }
      }

      if (newpos < newsize && oldFileIn.skip(bytesToSkipFromOld) != bytesToSkipFromOld) {
        System.err.println("error skipping in oldFileIn");
      }
    }

    // TODO: Check if at end of ctrlIn
    // TODO: Check if at the end of diffIn
    // TODO: Check if at the end of extraIn

    diffBlockIn.close();
    extraBlockIn.close();
    diffIn.close();
  }
}