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
package mslinks;

import mslinks.data.ItemID;
import mslinks.data.ItemIDDrive;
import mslinks.data.ItemIDFS;
import mslinks.data.ItemIDRegItem;
import mslinks.data.ItemIDRoot;
import mslinks.data.ItemIDUnknown;
import mslinks.io.ByteReader;

import java.io.IOException;
import java.util.LinkedList;

public class LinkTargetIDList extends LinkedList<ItemID> {

  public LinkTargetIDList(ByteReader data) throws IOException, ShellLinkException {
    int size = (int) data.read2bytes();
    int pos = data.getPosition();

    while (true) {
      int itemSize = (int) data.read2bytes();
      if (itemSize == 0)
        break;

      int typeFlags = data.read();
      var item = ItemID.createItem(typeFlags);
      item.load(data, itemSize - 3);
      add(item);
    }

    pos = data.getPosition() - pos;
    if (pos != size)
      throw new ShellLinkException("unexpected size of LinkTargetIDList");
  }

  public boolean canBuildPath() {
    for (ItemID i : this)
      if (i instanceof ItemIDUnknown)
        return false;
    return true;
  }

  public boolean canBuildAbsolutePath() {
    if (size() < 2)
      return false;

    var firstId = getFirst();
    if (!(firstId instanceof ItemIDRoot))
      return false;

    var rootId = (ItemIDRoot) firstId;
    if (!rootId.getClsid().equals(ItemIDRegItem.CLSID_COMPUTER))
      return false;

    var secondId = get(1);
    return secondId instanceof ItemIDDrive;
  }

  public String buildPath() {
    var path = new StringBuilder();
    if (!isEmpty()) {
      // when a link created by drag'n'drop menu from desktop, id list starts from filename directly
      var firstId = getFirst();
      if (firstId instanceof ItemIDFS)
        path.append("<Desktop>\\");

      for (ItemID i : this) {
        path.append(i.toString());
      }
    }
    return path.toString();
  }
}
