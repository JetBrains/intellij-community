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
package mslinks;

public class UnsupportedItemIDException extends ShellLinkException {
  public UnsupportedItemIDException(int typeFlags) {
    super(String.format("unsupported ItemID type %02x", typeFlags));
  }
}
