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

import mslinks.data.CNRLink;
import mslinks.data.LinkInfoFlags;
import mslinks.data.VolumeID;
import mslinks.io.ByteReader;

import java.io.IOException;

public class LinkInfo {
  private String localBasePath;
  private CNRLink cnrlink;
  private String commonPathSuffix;

  public LinkInfo(ByteReader data) throws IOException, ShellLinkException {
    int pos = data.getPosition();
    int size = (int) data.read4bytes();
    int hsize = (int) data.read4bytes();
    var lif = new LinkInfoFlags(data);
    int vidoffset = (int) data.read4bytes();
    int lbpoffset = (int) data.read4bytes();
    int cnrloffset = (int) data.read4bytes();
    int cpsoffset = (int) data.read4bytes();
    int lbpoffset_u = 0, cpfoffset_u = 0;
    if (hsize >= 0x24) {
      lbpoffset_u = (int) data.read4bytes();
      cpfoffset_u = (int) data.read4bytes();
    }

    if (lif.hasVolumeIDAndLocalBasePath()) {
      data.seek(pos + vidoffset - data.getPosition());
      //noinspection ResultOfObjectAllocationIgnored
      new VolumeID(data);
      data.seek(pos + lbpoffset - data.getPosition());
      localBasePath = data.readString(pos + size - data.getPosition());
    }
    if (lif.hasCommonNetworkRelativeLinkAndPathSuffix()) {
      data.seek(pos + cnrloffset - data.getPosition());
      cnrlink = new CNRLink(data);
      data.seek(pos + cpsoffset - data.getPosition());
      commonPathSuffix = data.readString(pos + size - data.getPosition());
    }
    if (lif.hasVolumeIDAndLocalBasePath() && lbpoffset_u != 0) {
      data.seek(pos + lbpoffset_u - data.getPosition());
      localBasePath = data.readUnicodeStringNullTerm((pos + size - data.getPosition()) >> 1);
    }
    if (lif.hasCommonNetworkRelativeLinkAndPathSuffix() && cpfoffset_u != 0) {
      data.seek(pos + cpfoffset_u - data.getPosition());
      commonPathSuffix = data.readUnicodeStringNullTerm((pos + size - data.getPosition()) >> 1);
    }

    data.seek(pos + size - data.getPosition());
  }

  public String getLocalBasePath() {
    return localBasePath;
  }

  public CNRLink getCommonNetworkRelativeLink() {
    return cnrlink;
  }

  public String getCommonPathSuffix() {
    return commonPathSuffix;
  }

  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public String buildPath() {
    if (localBasePath != null) {
      String path = localBasePath;
      if (commonPathSuffix != null && !commonPathSuffix.isEmpty()) {
        if (path.charAt(path.length() - 1) != java.io.File.separatorChar)
          path += java.io.File.separatorChar;
        path += commonPathSuffix;
      }
      return path;
    }

    if (cnrlink != null && commonPathSuffix != null)
      return cnrlink.getNetName() + "\\" + commonPathSuffix;

    return null;
  }
}
