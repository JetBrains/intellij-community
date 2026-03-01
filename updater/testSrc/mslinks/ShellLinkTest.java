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

import mslinks.io.ByteReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("UnnecessaryUnicodeEscape")
class ShellLinkTest {
	private static ShellLink createLink(byte[] data) throws IOException, ShellLinkException {
		var reader = new ByteReader(new ByteArrayInputStream(data));
		reader.setLittleEndian();
		return new ShellLink(reader);
	}

	// =====================================
	// === General link properties tests ===
	// =====================================

	@Test void TestLinkProperties() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.CONSOLE_LINK);

    var expectedTarget = "C:\\linktest\\folder\\pause.bat";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
		assertEquals("%SystemRoot%\\System32\\SHELL32.dll", link.getIconLocation());
		assertEquals("C:\\Windows", link.getWorkingDir());
		assertEquals(".\\folder\\pause.bat", link.getRelativePath());
	}

	@Test void TestLinkFlags() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.CONSOLE_LINK);
		var flags = link.getHeader().getLinkFlags();

		assertTrue(flags.hasLinkTargetIDList());
		assertTrue(flags.hasLinkInfo());
		assertFalse(flags.hasName());
		assertTrue(flags.hasRelativePath());
		assertTrue(flags.hasWorkingDir());
		assertTrue(flags.hasArguments());
		assertTrue(flags.hasIconLocation());
		assertTrue(flags.isUnicode());
		assertFalse(flags.forceNoLinkInfo());
		assertFalse(flags.hasExpString());
		assertFalse(flags.runInSeparateProcess());
		assertFalse(flags.hasDarwinID());
		assertFalse(flags.runAsUser());
		assertFalse(flags.hasExpIcon());
		assertFalse(flags.noPidlAlias());
		assertFalse(flags.runWithShimLayer());
		assertFalse(flags.forceNoLinkTrack());
		assertTrue(flags.enableTargetMetadata());
		assertFalse(flags.disableLinkPathTracking());
		assertFalse(flags.disableKnownFolderTracking());
		assertFalse(flags.disableKnownFolderAlias());
		assertFalse(flags.allowLinkToLink());
		assertFalse(flags.unaliasOnSave());
		assertFalse(flags.preferEnvironmentPath());
		assertFalse(flags.keepLocalIDListForUNCTarget());
	}

	@Test void TestLinkInfo() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.CONSOLE_LINK);
		var linkInfo = link.getLinkInfo();

		assertEquals("C:\\linktest\\folder\\pause.bat", linkInfo.buildPath());
		assertEquals("C:\\linktest\\folder\\pause.bat", linkInfo.getLocalBasePath());
        assertNull(linkInfo.getCommonPathSuffix());
		assertNull(linkInfo.getCommonNetworkRelativeLink());
	}

	// =====================================
	// ======= Special cases tests =========
	// =====================================

	@Test void TestRunAsAdmin() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.CONSOLE_LINK_ADMIN);
		var flags = link.getHeader().getLinkFlags();
		assertTrue(flags.runAsUser());
	}

	@Test void TestExeLinkProperties() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.EXE_LINK);

    var expectedTarget = "C:\\Program Files\\7-Zip\\7zFM.exe";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
        assertNull(link.getIconLocation());
		assertEquals("C:\\Program Files\\7-Zip", link.getWorkingDir());
		assertEquals("..\\Program Files\\7-Zip\\7zFM.exe", link.getRelativePath());
	}

	@Test void TestMediaFileLinkProperties() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.TEXT_FILE_LINK);

    var expectedTarget = "C:\\linktest\\folder\\textfile.txt";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
        assertNull(link.getIconLocation());
		assertEquals("C:\\linktest\\folder", link.getWorkingDir());
		assertEquals(".\\folder\\textfile.txt", link.getRelativePath());
	}

	@Test void TestUnicodePath() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.UNICODE_TARGET_LINK);

    var expectedTarget = "C:\\linktest\\folder\\\u03B1\u03B1\u03B1.txt";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
	}

	@Test void TestNetworkSharePath() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.SHARED_FILE_LINK);
		var header = link.getHeader();
		var linkInfo = link.getLinkInfo();

    var expectedTarget = "\\\\LAPTOP\\SHARE\\testfile.txt";

		assertFalse(header.getLinkFlags().hasLinkTargetIDList());
		assertNull(link.getTargetIdList());

		assertTrue(header.getLinkFlags().hasLinkInfo());
		assertNotNull(linkInfo);

		assertEquals(expectedTarget, linkInfo.buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
	}

	@Test void TestNetworkShareLinkProperties() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.SHARED_FILE_LINK);
		var linkInfo = link.getLinkInfo();

        assertNull(linkInfo.getLocalBasePath());
		assertEquals("testfile.txt", linkInfo.getCommonPathSuffix());

		var netLink = linkInfo.getCommonNetworkRelativeLink();
		assertNotNull(netLink);
		assertEquals("\\\\LAPTOP\\SHARE", netLink.getNetName());
	}

	@Test void TestNetworkDrivePath() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.NETWORK_DRIVE_FILE_LINK);

    var expectedTarget = "Z:\\testfile.txt";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
	}

	@Test void TestNetworkDriveLinkProperties() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.NETWORK_DRIVE_FILE_LINK);
		var linkInfo = link.getLinkInfo();

    var expectedTarget = "\\\\laptop\\share\\testfile.txt";

        assertNull(linkInfo.getLocalBasePath());
		assertEquals("testfile.txt", linkInfo.getCommonPathSuffix());
		assertEquals(expectedTarget, linkInfo.buildPath());

		var netLink = linkInfo.getCommonNetworkRelativeLink();
		assertNotNull(netLink);
		assertEquals("\\\\laptop\\share", netLink.getNetName());
	}

	@Test void TestNetworkShareUnicodePath() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.SHARED_FOLDER_UNICODE_LINK);
		var header = link.getHeader();
		var linkInfo = link.getLinkInfo();

    var expectedTarget = "\\\\LAPTOP\\\u0391\u0391\u0391\\\u03B1\u03B1\u03B1.txt";

		assertFalse(header.getLinkFlags().hasLinkTargetIDList());
		assertNull(link.getTargetIdList());

		assertTrue(header.getLinkFlags().hasLinkInfo());
		assertNotNull(linkInfo);

		assertEquals(expectedTarget, linkInfo.buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
	}

	@Test void TestDirectoryLink() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.DIRECTORY_LINK);

    var expectedTarget = "C:\\linktest\\folder\\";

		assertTrue(link.getTargetIdList().canBuildAbsolutePath());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals(expectedTarget, link.resolveTarget());
	}

	@Test void TestDesktopScreenLinkWin10() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.DESKTOP_WIN10);

    var expectedTarget = "<Desktop>\\pause.bat";

		assertFalse(link.getTargetIdList().canBuildAbsolutePath());
		assertTrue(link.getTargetIdList().canBuildPath());
		assertEquals(1, link.getTargetIdList().size());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals("C:\\Users\\admin\\Desktop\\pause.bat", link.resolveTarget());
	}

	@Test void TestDesktopFolderLinkWin10() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.DESKTOP_WIN10_FOLDER);

    var expectedTarget = "<Desktop>\\pause.bat";

		assertFalse(link.getTargetIdList().canBuildAbsolutePath());
		assertTrue(link.getTargetIdList().canBuildPath());
		assertEquals(3, link.getTargetIdList().size());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals("C:\\Users\\admin\\Desktop\\pause.bat", link.resolveTarget());
	}

	@Test void TestDocumentsLinkWin10() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.DOCUMENTS_WIN10);

    var expectedTarget = "<LocalDocuments>\\pause.bat";

		assertFalse(link.getTargetIdList().canBuildAbsolutePath());
		assertTrue(link.getTargetIdList().canBuildPath());
		assertEquals(3, link.getTargetIdList().size());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals("C:\\Users\\admin\\Documents\\pause.bat", link.resolveTarget());
	}

	@Test void TestDownloadsLinkWin10() throws IOException, ShellLinkException {
		var link = createLink(ShellLinkTestData.DOWNLOADS_WIN10);

    var expectedTarget = "<LocalDownloads>\\pause.bat";

		assertFalse(link.getTargetIdList().canBuildAbsolutePath());
		assertTrue(link.getTargetIdList().canBuildPath());
		assertEquals(3, link.getTargetIdList().size());
		assertEquals(expectedTarget, link.getTargetIdList().buildPath());
		assertEquals("C:\\Users\\admin\\Downloads\\pause.bat", link.resolveTarget());
	}
}
