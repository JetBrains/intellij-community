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

import mslinks.data.LinkFlags;
import mslinks.extra.EnvironmentVariable;
import mslinks.io.ByteReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ShellLink {
  private ShellLinkHeader header;
  private LinkTargetIDList idList;
  private LinkInfo info;
  private String relativePath;
  private String workingDir;
  private String iconLocation;
  private EnvironmentVariable envBlock;
  private Path linkFileSource;

  public ShellLink(Path file) throws IOException, ShellLinkException {
    try (var reader = new ByteReader(Files.newInputStream(file))) {
      parse(reader);
    }
    linkFileSource = file.toAbsolutePath();
  }

  public ShellLink(ByteReader reader) throws IOException, ShellLinkException {
    try (reader) {
      parse(reader);
    }
  }

  private void parse(ByteReader data) throws ShellLinkException, IOException {
    header = new ShellLinkHeader(data);
    LinkFlags lf = header.getLinkFlags();
    if (lf.hasLinkTargetIDList())
      idList = new LinkTargetIDList(data);
    if (lf.hasLinkInfo())
      info = new LinkInfo(data);
    if (lf.hasName())
      /*name =*/ data.readUnicodeStringSizePadded();
    if (lf.hasRelativePath())
      relativePath = data.readUnicodeStringSizePadded();
    if (lf.hasWorkingDir())
      workingDir = data.readUnicodeStringSizePadded();
    if (lf.hasArguments())
      /*cmdArgs =*/ data.readUnicodeStringSizePadded();
    if (lf.hasIconLocation())
      iconLocation = data.readUnicodeStringSizePadded();

    while (true) {
      int size = (int) data.read4bytes();
      if (size < 4) break;
      int sign = (int) data.read4bytes();
      if (sign == EnvironmentVariable.signature)
        envBlock = new EnvironmentVariable(data, size);
      else
        data.seek(size - 8);
    }
  }

  public ShellLinkHeader getHeader() {
    return header;
  }

  public LinkInfo getLinkInfo() {
    return info;
  }

  public LinkTargetIDList getTargetIdList() {
    return idList;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public String getWorkingDir() {
    return workingDir;
  }

  public String getIconLocation() {
    return iconLocation;
  }

  public String resolveTarget() {
    if (header.getLinkFlags().hasLinkTargetIDList() && idList != null && idList.canBuildAbsolutePath())
      return idList.buildPath();

    if (header.getLinkFlags().hasLinkInfo() && info != null) {
      String path = info.buildPath();
      if (path != null)
        return path;
    }

    if (linkFileSource != null && header.getLinkFlags().hasRelativePath() && relativePath != null)
      return linkFileSource.resolveSibling(relativePath).normalize().toString();

    if (envBlock != null && !envBlock.getVariable().isEmpty())
      return envBlock.getVariable();

    if (header.getLinkFlags().hasLinkTargetIDList() && idList != null && idList.canBuildPath())
      return idList.buildPath();

    return "<unknown>";
  }
}
