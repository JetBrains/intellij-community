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

import mslinks.io.ByteReader;

import java.io.IOException;

public class LinkFlags extends BitSet32 {
  public LinkFlags(ByteReader data) throws IOException {
    super(data);
    reset();
  }

  private void reset() {
    clear(11);
    clear(16);
    for (int i = 27; i < 32; i++)
      clear(i);
  }

  public boolean hasLinkTargetIDList() {
    return get(0);
  }

  public boolean hasLinkInfo() {
    return get(1);
  }

  public boolean hasName() {
    return get(2);
  }

  public boolean hasRelativePath() {
    return get(3);
  }

  public boolean hasWorkingDir() {
    return get(4);
  }

  public boolean hasArguments() {
    return get(5);
  }

  public boolean hasIconLocation() {
    return get(6);
  }

  public boolean isUnicode() {
    return get(7);
  }

  public boolean forceNoLinkInfo() {
    return get(8);
  }

  public boolean hasExpString() {
    return get(9);
  }

  public boolean runInSeparateProcess() {
    return get(10);
  }

  public boolean hasDarwinID() {
    return get(12);
  }

  public boolean runAsUser() {
    return get(13);
  }

  public boolean hasExpIcon() {
    return get(14);
  }

  public boolean noPidlAlias() {
    return get(15);
  }

  public boolean runWithShimLayer() {
    return get(17);
  }

  public boolean forceNoLinkTrack() {
    return get(18);
  }

  public boolean enableTargetMetadata() {
    return get(19);
  }

  public boolean disableLinkPathTracking() {
    return get(20);
  }

  public boolean disableKnownFolderTracking() {
    return get(21);
  }

  public boolean disableKnownFolderAlias() {
    return get(22);
  }

  public boolean allowLinkToLink() {
    return get(23);
  }

  public boolean unaliasOnSave() {
    return get(24);
  }

  public boolean preferEnvironmentPath() {
    return get(25);
  }

  public boolean keepLocalIDListForUNCTarget() {
    return get(26);
  }
}
