/*
	https://github.com/DmitriiShamrikov/mslinks

	Copyright (c) 2022 Dmitrii Shamrikov

	Licensed under the WTFPL
	You may obtain a copy of the License at

	http://www.wtfpl.net/about/

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*/
package mslinks.data;

import mslinks.ShellLinkException;
import mslinks.io.ByteReader;

import java.io.IOException;

public class ItemIDUnknown extends ItemID {
  public ItemIDUnknown(int flags) {
    super(flags);
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    int startPos = br.getPosition();

    super.load(br, maxSize);

    int bytesRead = br.getPosition() - startPos;
    byte[] data = new byte[maxSize - bytesRead];
    //noinspection ResultOfMethodCallIgnored
    br.read(data);
  }

  @Override
  public String toString() {
    return String.format("<ItemIDUnknown 0x%02X>", typeFlags);
  }
}
