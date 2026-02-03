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
import mslinks.io.ByteReader;

import java.io.IOException;
import java.util.Map;

public abstract class ItemIDRegItem extends ItemID {
  public static final GUID CLSID_COMPUTER = new GUID("{20d04fe0-3aea-1069-a2d8-08002b30309d}");

  private static final Map<GUID, String> names = Map.ofEntries(
    Map.entry(CLSID_COMPUTER, "Computer"),
    Map.entry(new GUID("{D20EA4E1-3957-11D2-A40B-0C5020524153}"), "CommonAdministrativeTools"),
    Map.entry(new GUID("{450D8FBA-AD25-11D0-98A8-0800361B1103}"), "Documents"),
    Map.entry(new GUID("{645FF040-5081-101B-9F08-00AA002F954E}"), "RecycleBin"),
    Map.entry(new GUID("{D20EA4E1-3957-11D2-A40B-0C5020524152}"), "Fonts"),
    Map.entry(new GUID("{D34A6CA6-62C2-4C34-8A7C-14709C1AD938}"), "Links"),
    Map.entry(new GUID("{B155BDF8-02F0-451E-9A26-AE317CFD7779}"), "NetHood"),
    Map.entry(new GUID("{ED50FC29-B964-48A9-AFB3-15EBB9B97F36}"), "PrintHood"),
    Map.entry(new GUID("{4336A54D-038B-4685-AB02-99BB52D3FB8B}"), "Public"),
    Map.entry(new GUID("{1F3427C8-5C10-4210-AA03-2EE45287D668}"), "UserPinned"),
    Map.entry(new GUID("{0DB7E03F-FC29-4DC6-9020-FF41B59E513A}"), "3DObjects"),
    Map.entry(new GUID("{B4BFCC3A-DB2C-424C-B029-7FE99A87C641}"), "Desktop"),
    Map.entry(new GUID("{374DE290-123F-4565-9164-39C4925E467B}"), "Downloads"),
    Map.entry(new GUID("{D3162B92-9365-467A-956B-92703ACA08AF}"), "LocalDocuments"),
    Map.entry(new GUID("{088E3905-0323-4B02-9826-5D99428E115F}"), "LocalDownloads"),
    Map.entry(new GUID("{3DFDF296-DBEC-4FB4-81D1-6A3438BCF4DE}"), "LocalMusic"),
    Map.entry(new GUID("{24AD3AD4-A569-4530-98E1-AB02F9417AA8}"), "LocalPictures"),
    Map.entry(new GUID("{F86FA3AB-70D2-4FC7-9C99-FCBF05467F3A}"), "LocalVideos"),
    Map.entry(new GUID("{1CF1260C-4DD0-4EBB-811F-33C572699FDE}"), "MyMusic"),
    Map.entry(new GUID("{3ADD1653-EB32-4CB0-BBD7-DFA0ABB5ACCA}"), "MyPictures"),
    Map.entry(new GUID("{A0953C92-50DC-43BF-BE83-3742FED03C9C}"), "MyVideo"),
    Map.entry(new GUID("{018D5C66-4533-4307-9B53-224DE2ED1FE6}"), "OneDrive"),
    Map.entry(new GUID("{A8CDFF1C-4878-43BE-B5FD-F8091C1C60D0}"), "Personal"),
    Map.entry(new GUID("{F8278C54-A712-415B-B593-B77A2BE0DDA9}"), "Profile"),
    Map.entry(new GUID("{5B934B42-522B-4C34-BBFE-37A3EF7B9C90}"), "Public_1")
  );

  protected GUID clsid;

  public ItemIDRegItem(int flags) {
    super(flags);
  }

  @Override
  public void load(ByteReader br, int maxSize) throws IOException, ShellLinkException {
    super.load(br, maxSize);
    //noinspection ResultOfMethodCallIgnored
    br.read(); // order
    clsid = new GUID(br);
  }

  @Override
  public String toString() {
    var name = names.get(clsid);
    if (name == null) name = getClass().getSimpleName();
    return "<" + name + ">\\";
  }

  public GUID getClsid() {
    return clsid;
  }
}
