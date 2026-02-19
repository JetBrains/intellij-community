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

public class ItemIDRoot extends ItemIDRegItem {
  public ItemIDRoot(int flags) throws UnsupportedItemIDException {
    super(flags | GROUP_ROOT);

    int subType = typeFlags & ID_TYPE_INGROUPMASK;
    if (subType != TYPE_ROOT_REGITEM)
      throw new UnsupportedItemIDException(typeFlags);
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    int endPos = br.getPosition() + maxSize;
    super.load(br, maxSize);
    br.seekTo(endPos);
  }

  @Override
  public String toString() {
    return clsid.equals(CLSID_COMPUTER) ? "" : super.toString();
  }
}
