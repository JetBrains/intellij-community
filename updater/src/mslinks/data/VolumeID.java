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

public class VolumeID {
  public VolumeID(ByteReader data) throws ShellLinkException, IOException {
    int pos = data.getPosition();
    int size = (int) data.read4bytes();
    if (size <= 0x10)
      throw new ShellLinkException();

    /*dt = (int)*/
    data.read4bytes();
    /*dsn = (int)*/
    data.read4bytes();
    int vloffset = (int) data.read4bytes();
    boolean u = false;
    if (vloffset == 0x14) {
      vloffset = (int) data.read4bytes();
      u = true;
    }

    data.seek(pos + vloffset - data.getPosition());

    if (u) {
      //noinspection StatementWithEmptyBody
      while ((char) data.read2bytes() != 0) ;
    }
    else {
      //noinspection StatementWithEmptyBody
      while (data.read() != 0) ;
    }
  }
}
