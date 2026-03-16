/*
	https://github.com/DmitriiShamrikov/mslinks

	Copyright (c) 2015 Dmitrii Shamrikov

	Licensed under the WTFPL
	You may obtain a copy of the License at

	http://www.wtfpl.net/about/

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/
package mslinks.data;

import mslinks.io.ByteReader;
import mslinks.io.Bytes;

import java.io.IOException;

public class GUID {
  private final int d1;
  private final short d2;
  private final short d3;
  private final short d4;
  private final long d5;

  public GUID(ByteReader data) throws IOException {
    d1 = (int) data.read4bytes();
    d2 = (short) data.read2bytes();
    d3 = (short) data.read2bytes();
    data.changeEndiannes();
    d4 = (short) data.read2bytes();
    d5 = data.read6bytes();
    data.changeEndiannes();
  }

  public GUID(String s) {
    if (s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}')
      s = s.substring(1, s.length() - 1);
    String[] p = s.split("-");

    byte[] b = parse(p[0]);
    d1 = Bytes.makeIntB(b[0], b[1], b[2], b[3]);
    b = parse(p[1]);
    d2 = Bytes.makeShortB(b[0], b[1]);
    b = parse(p[2]);
    d3 = Bytes.makeShortB(b[0], b[1]);
    d4 = (short) Long.parseLong(p[3], 16);
    d5 = Long.parseLong(p[4], 16);
  }

  private static byte[] parse(String s) {
    byte[] b = new byte[s.length() >> 1];
    for (int i = 0, j = 0; j < s.length(); i++, j += 2)
      b[i] = (byte) Long.parseLong(s.substring(j, j + 2), 16);
    return b;
  }

  @Override
  public String toString() {
    return String.format("%08X-%04X-%04X-%04X-%012X", d1, d2, d3, d4, d5);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this)
      return true;

    if (o == null || getClass() != o.getClass())
      return false;

    GUID g = (GUID) o;
    return d1 == g.d1 && d2 == g.d2 && d3 == g.d3 && d4 == g.d4 && d5 == g.d5;
  }

  @Override
  public int hashCode() {
    return (int) (d1 ^ d2 ^ d3 ^ d4 ^ ((d5 & 0xffffffff00000000L) >> 32) ^ (d5 & 0xffffffffL));
  }
}
