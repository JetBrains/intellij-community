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

public class ItemIDRegFolder extends ItemIDRegItem {
  public ItemIDRegFolder(int flags) throws UnsupportedItemIDException {
    super(flags | GROUP_COMPUTER);

    int subType = typeFlags & ID_TYPE_INGROUPMASK;
    if (subType != TYPE_DRIVE_REGITEM)
      throw new UnsupportedItemIDException(typeFlags);
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    int endPos = br.getPosition() + maxSize;
    super.load(br, maxSize);
    // depending on ItemIDRoot there might be some padding before this item's clsid
    // but it is 0 anyway since only CLSID_COMPUTER is supported for now

    // see CRegFolder::_bFlagsLegacy, CRegFolder::_cbPadding and IDREGITEMEX (regfldr.cpp)
    // search for CRegFolder_CreateInstance, there are several cases where regitem is used, not only 0x2e
    // the padding and the legacy flags are actually used for TYPE_CONTROL_REGITEM/TYPE_CONTROL_REGITEM_EX

    br.seekTo(endPos);
  }
}
