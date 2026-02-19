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

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ItemIDFS extends ItemID {
  public static final int HIDDEN_ID_IDFOLDEREX = 4;          //  IDFOLDEREX, extended data for CFSFolder
  public static final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;

  protected int size;
  protected short attributes;
  protected String shortname;
  protected String longname;

  public ItemIDFS(int flags) throws UnsupportedItemIDException {
    super(flags | GROUP_FS);
    onTypeFlagsChanged();
  }

  private void onTypeFlagsChanged() throws UnsupportedItemIDException {
    int subType = typeFlags & ID_TYPE_INGROUPMASK;
    if ((subType & TYPE_FS_DIRECTORY) == 0 && (subType & TYPE_FS_FILE) == 0)
      throw new UnsupportedItemIDException(typeFlags);

    // don't allow flipping unicode flag at will to avoid inconsistency
    if (longname != null) {
      if (isLongFilename(longname))
        typeFlags |= TYPE_FS_UNICODE;
      else
        typeFlags &= ~TYPE_FS_UNICODE;
    }

    // attribute directory flag should match the typeFlag directory flag
    if ((subType & TYPE_FS_DIRECTORY) != 0)
      attributes |= FILE_ATTRIBUTE_DIRECTORY;
    else
      attributes &= ~FILE_ATTRIBUTE_DIRECTORY;
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    // 3 bytes are the size (2) and the type (1) initially parsed in LinkTargetIDList
    // but they are considered part of the ItemID for calculating offsets
    int startPos = br.getPosition() - 3;
    int endPos = startPos + maxSize + 3;

    super.load(br, maxSize);

    //noinspection ResultOfMethodCallIgnored
    br.read(); // IDFOLDER struct doesn't have this byte but it does exist in data. Probably it's just padding
    size = (int) br.read4bytes();
    br.read2bytes(); // date modified
    br.read2bytes(); // time modified
    attributes = (short) br.read2bytes();

    if ((typeFlags & TYPE_FS_UNICODE) != 0) {
      longname = br.readUnicodeStringNullTerm(endPos - br.getPosition());
    }
    shortname = br.readString(endPos - br.getPosition());

    int restOfDataSize = endPos - br.getPosition();
    if (restOfDataSize <= 2) {
      br.seek(restOfDataSize);
      return;
    }

    // last 2 bytes are the offset to the hidden list
    int bytesParsed = br.getPosition() - startPos;
    byte[] dataChunk = new byte[restOfDataSize - 2];
    //noinspection ResultOfMethodCallIgnored
    br.read(dataChunk, 0, dataChunk.length);
    int hiddenOffset = (int) br.read2bytes();
    if (hiddenOffset == 0 || hiddenOffset < bytesParsed) {
      return;
    }

    int offsetInDataChunk = hiddenOffset - bytesParsed;
    var hbr = new ByteReader(new ByteArrayInputStream(dataChunk, offsetInDataChunk, dataChunk.length));
    loadHiddenPart(hbr, dataChunk.length + 2 - offsetInDataChunk);
  }

  protected void loadHiddenPart(ByteReader br, int maxSize) throws IOException {
    while (true) {
      int startPos = br.getPosition();
      int hiddenSize = (int) br.read2bytes();
      int hiddenVersion = (int) br.read2bytes();
      int hiddenIdField = (int) br.read4bytes();
      int hiddenIdMagic = (hiddenIdField & 0xFFFF0000) >>> 16;
      int hiddenId = hiddenIdField & 0xFFFF;

      int hiddenEndPos = br.getPosition() - 8 + hiddenSize;
      if (hiddenEndPos > maxSize) {
        break;
      }

      if (hiddenIdMagic != 0xBEEF) {
        br.seek(hiddenSize - 8);
        continue;
      }

      if (hiddenId == HIDDEN_ID_IDFOLDEREX && hiddenVersion >= 3) { // IDFX_V1
        br.read4bytes(); // date & time created
        br.read4bytes(); // date & time accessed
        int offsetNameUnicode = (int) br.read2bytes();
        br.read2bytes(); // offResourceA
        int unicodeNamePos = startPos + offsetNameUnicode;
        br.seek(unicodeNamePos - br.getPosition());
        longname = br.readUnicodeStringNullTerm(startPos + hiddenSize - br.getPosition());

        // we don't serialize hidden parts so add unicode flag
        if (!longname.equals(shortname))
          typeFlags |= TYPE_FS_UNICODE;
        break;
      }
    }
  }

  @Override
  public String toString() {
    String name = (typeFlags & TYPE_FS_UNICODE) != 0 ? longname : shortname;
    if ((typeFlags & TYPE_FS_DIRECTORY) != 0)
      name += "\\";
    return name;
  }

  public int getSize() {
    return size;
  }

  public short getAttributes() {
    return attributes;
  }

  public String getName() {
    if (longname != null && !longname.isEmpty())
      return longname;
    return shortname;
  }
}
