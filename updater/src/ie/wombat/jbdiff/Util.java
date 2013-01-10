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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Joe Desbonnet, jdesbonnet@gmail.com
 *
 */
public class Util {

	/**
	 * Equiv of C library memcmp().
	 * 
	 * @param s1
	 * @param s1offset
	 * @param s2
	 * @param n
	 * @return
	 */
	/*
	public final static int memcmp(byte[] s1, int s1offset, byte[] s2, int s2offset, int n) {
	
		if ((s1offset + n) > s1.length) {
			n = s1.length - s1offset;
		}
		if ((s2offset + n) > s2.length) {
			n = s2.length - s2offset;
		}
		for (int i = 0; i < n; i++) {
			if (s1[i + s1offset] != s2[i + s2offset]) {
				return s1[i + s1offset] < s2[i + s2offset] ? -1 : 1;
			}
		}
		
		return 0;
	}
	*/

	/**
	 * Equiv of C library memcmp().
	 * 
	 * @param s1
	 * @param s1offset
	 * @param s2
	 * @param n
	 * @return
	 */
	public final static int memcmp(byte[] s1, int s1offset, byte[] s2, int s2offset) {
	
		int n = s1.length - s1offset;
		
		if (n > (s2.length-s2offset)) {
			n = s2.length-s2offset;
		}
		for (int i = 0; i < n; i++) {
			if (s1[i + s1offset] != s2[i + s2offset]) {
				return s1[i + s1offset] < s2[i + s2offset] ? -1 : 1;
			}
		}
		
		return 0;
	}

	public static final boolean readFromStream (InputStream in, byte[] buf, int offset, int len)
	throws IOException 
	{
			
		int totalBytesRead = 0;
		int nbytes;
		
		while ( totalBytesRead < len) {
			nbytes = in.read(buf,offset+totalBytesRead,len-totalBytesRead);
			if (nbytes < 0) {
				System.err.println ("readFromStream(): returning prematurely. Read " 
						+ totalBytesRead + " bytes");
				return false;
			}
			totalBytesRead+=nbytes;
		}
		
		return true;
	}

}
