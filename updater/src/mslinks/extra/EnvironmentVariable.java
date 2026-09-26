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
package mslinks.extra;

import mslinks.ShellLinkException;
import mslinks.io.ByteReader;

import java.io.IOException;

public class EnvironmentVariable {

  public static final int signature = 0xA0000001;
  public static final int size = 0x314;

  private final String variable;

  public EnvironmentVariable() {
    variable = "";
  }

  public EnvironmentVariable(ByteReader br, int sz) throws ShellLinkException, IOException {
    if (sz != size)
      throw new ShellLinkException();

    int pos = br.getPosition();
    String variable = br.readString(260);
    br.seekTo(pos + 260);

    pos = br.getPosition();
    String unicodeStr = br.readUnicodeStringNullTerm(260);
    br.seekTo(pos + 520);
    if (unicodeStr != null && !unicodeStr.isEmpty())
      variable = unicodeStr;

    this.variable = variable;
  }

  public String getVariable() {
    return variable;
  }
}
