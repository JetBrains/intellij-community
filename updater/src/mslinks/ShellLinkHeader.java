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

import mslinks.data.GUID;
import mslinks.data.LinkFlags;
import mslinks.io.ByteReader;

import java.io.IOException;

public class ShellLinkHeader {
  private static final int headerSize = 0x0000004C;
  private static final GUID clsid = new GUID("00021401-0000-0000-C000-000000000046");

  private final LinkFlags lf;

  public ShellLinkHeader(ByteReader data) throws ShellLinkException, IOException {
    int size = (int) data.read4bytes();
    if (size != headerSize)
      throw new ShellLinkException();
    GUID g = new GUID(data);
    if (!g.equals(clsid))
      throw new ShellLinkException();
    lf = new LinkFlags(data);
    data.read4bytes();  // FileAttributesFlags
    data.read8bytes();  // creationTime
    data.read8bytes();  // accessTime
    data.read8bytes();  // writeTime
    /*fileSize = (int)*/
    data.read4bytes();
    /*iconIndex = (int)*/
    data.read4bytes();
    /*showCommand = (int)*/
    data.read4bytes();
    data.read2bytes();  // HotKeyFlags
    data.read2bytes();
    data.read8bytes();
  }

  public LinkFlags getLinkFlags() {
    return lf;
  }
}
