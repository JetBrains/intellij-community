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

import java.io.IOException;

public class BitSet32 {
  private int d;

  public BitSet32(ByteReader data) throws IOException {
    d = (int) data.read4bytes();
  }

  protected boolean get(int i) {
    return (d & (1 << i)) != 0;
  }

  protected void clear(int i) {
    d &= ~(1 << i);
  }
}
