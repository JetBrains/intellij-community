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
import mslinks.UnsupportedItemIDException;
import mslinks.io.ByteReader;

import java.io.IOException;
import java.util.regex.Pattern;

public class ItemIDDrive extends ItemID {

  protected String name;

  public ItemIDDrive(int flags) throws UnsupportedItemIDException {
    super(flags | GROUP_COMPUTER);

    int subType = typeFlags & ID_TYPE_INGROUPMASK;
    if (subType == 0)
      throw new UnsupportedItemIDException(typeFlags);
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    int startPos = br.getPosition();
    int endPos = startPos + maxSize;

    super.load(br, maxSize);

    var s = br.readString(4);
    if (Pattern.matches("\\w:\\\\", s))
      name = s;
    else if (Pattern.matches("\\w:", s))
      name = s + "\\";
    else if (Pattern.matches("\\w", s))
      name = s + ":\\";
    else
      throw new ShellLinkException("wrong drive name: " + s);

    // 8 bytes: drive size
    // 8 bytes: drive free size
    // 1 byte: 0/1 - has drive extension
    // 1 byte: 0/1 - drive extension has class id
    // 16 bytes: clsid - only possible value is CDBurn
    br.seekTo(endPos);
  }

  @Override
  public String toString() {
    return name;
  }
}
