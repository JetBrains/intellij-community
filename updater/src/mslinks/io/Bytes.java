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
package mslinks.io;

public final class Bytes {
  private Bytes() { }

  public static short makeShortB(byte b0, byte b1) {
    return (short) ((i(b0) << 8) | i(b1));
  }

  public static int makeIntB(byte b0, byte b1, byte b2, byte b3) {
    return (i(b0) << 24) | (i(b1) << 16) | (i(b2) << 8) | i(b3);
  }

  private static int i(byte b) {
    return b & 0xff;
  }
}
