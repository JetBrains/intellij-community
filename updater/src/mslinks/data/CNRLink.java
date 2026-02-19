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

import mslinks.ShellLinkException;
import mslinks.io.ByteReader;

import java.io.IOException;

public class CNRLink {
  private String netname;

  public CNRLink(ByteReader data) throws ShellLinkException, IOException {
    int pos = data.getPosition();
    int size = (int) data.read4bytes();
    if (size < 0x14)
      throw new ShellLinkException();
    var flags = new CNRLinkFlags(data);
    int nnoffset = (int) data.read4bytes();
    int dnoffset = (int) data.read4bytes();
    if (!flags.isValidDevice())
      dnoffset = 0;
    /*nptype = (int)*/data.read4bytes();

    int nnoffset_u = 0, dnoffset_u = 0;
    if (nnoffset > 0x14) {
      nnoffset_u = (int) data.read4bytes();
      dnoffset_u = (int) data.read4bytes();
    }

    data.seek(pos + nnoffset - data.getPosition());
    netname = data.readString(pos + size - data.getPosition());
    if (dnoffset != 0) {
      data.seek(pos + dnoffset - data.getPosition());
      /*devname =*/data.readString(pos + size - data.getPosition());
    }

    if (nnoffset_u != 0) {
      data.seek(pos + nnoffset_u - data.getPosition());
      netname = data.readUnicodeStringNullTerm(pos + size - data.getPosition());
    }

    if (dnoffset_u != 0) {
      data.seek(pos + dnoffset_u - data.getPosition());
      /*devname =*/data.readUnicodeStringNullTerm(pos + size - data.getPosition());
    }
  }

  public String getNetName() {
    return netname;
  }
}
