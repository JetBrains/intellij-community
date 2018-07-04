/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.MultiMap;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.SvnInfoHandler;
import org.jetbrains.idea.svn.status.CmdStatusClient;
import org.jetbrains.idea.svn.status.PortableStatus;
import org.jetbrains.idea.svn.status.SvnStatusHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class SvnParseCommandLineParseTest extends TestCase {

  public static final String LINUX_ROOT = "/c7181320/";

  public void testInfo() throws Exception {
    final String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<info>\n" +
                     "<entry\n" +
                     "   path=\"ReduceReturnOrYieldBreakTransformation.cs\"\n" +
                     "   revision=\"91603\"\n" +
                     "   kind=\"file\">\n" +
                     "<url>http://svn.labs.intellij.net/resharper/trunk/ReSharper/src/Decompiler.Core/Src/Transformations/StatementStructure/ReduceReturnOrYieldBreakTransformation.cs</url>\n" +
                     "<repository>\n" +
                     "<root>http://svn.labs.intellij.net/resharper</root>\n" +
                     "<uuid>ed0594e5-7722-0410-9c76-949374689613</uuid>\n" +
                     "</repository>\n" +
                     "<wc-info>\n" +
                     "<wcroot-abspath>C:/TestProjects/sortedProjects/Subversion/Resharper17short</wcroot-abspath>\n" +
                     "<schedule>normal</schedule>\n" +
                     "<depth>infinity</depth>\n" +
                     "<text-updated>2012-01-20T11:25:32.625000Z</text-updated>\n" +
                     "<checksum>7af8adacb93afaa48b2cfb76de605824c220983a</checksum>\n" +
                     "</wc-info>\n" +
                     "<commit\n" +
                     "   revision=\"87972\">\n" +
                     "<author>Slava.Trenogin</author>\n" +
                     "<date>2011-10-06T21:27:41.539022Z</date>\n" +
                     "</commit>\n" +
                     "</entry>\n" +
                     "</info>";

    final Info[] info = new Info[1];
    final SvnInfoHandler handler = new SvnInfoHandler(new File("C:/base/"), info1 -> info[0] = info1);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(s.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);

    Assert.assertNotNull(info[0]);
  }

  public void testStatus() throws Exception {
    final String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<status>\n" +
                     "<target\n" +
                     "   path=\".\">\n" +
                     "<entry\n" +
                     "   path=\".\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   item=\"incomplete\"\n" +
                     "   revision=\"92339\">\n" +
                     "<commit\n" +
                     "   revision=\"91672\">\n" +
                     "<author>qx</author>\n" +
                     "<date>2012-01-27T16:11:06.069351Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"Decompiler\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   tree-conflicted=\"true\"\n" +
                     "   item=\"missing\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"Help\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   item=\"incomplete\"\n" +
                     "   revision=\"92339\">\n" +
                     "<commit\n" +
                     "   revision=\"91555\">\n" +
                     "<author>Egor.Malyshev</author>\n" +
                     "<date>2012-01-18T18:05:07.328519Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"Help\\XML\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   item=\"incomplete\"\n" +
                     "   revision=\"92339\">\n" +
                     "<commit\n" +
                     "   revision=\"91555\">\n" +
                     "<author>Egor.Malyshev</author>\n" +
                     "<date>2012-01-18T18:05:07.328519Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"Help\\XML\\images\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   item=\"incomplete\"\n" +
                     "   revision=\"92339\">\n" +
                     "<commit\n" +
                     "   revision=\"91170\">\n" +
                     "<author>Maria.Egorkina</author>\n" +
                     "<date>2011-12-20T13:26:52.217550Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"Platform\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"external\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"ReSharper.ipr\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"91677\"\n" +
                     "   props=\"normal\">\n" +
                     "<commit\n" +
                     "   revision=\"87631\">\n" +
                     "<author>Alexey.Kuptsov</author>\n" +
                     "<date>2011-09-30T11:25:10.391467Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"ReSharper.iws\">\n" +
                     "<wc-status\n" +
                     "   item=\"ignored\"\n" +
                     "   props=\"none\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"measure.bat\">\n" +
                     "<wc-status\n" +
                     "   item=\"unversioned\"\n" +
                     "   props=\"none\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"src\\Daemon\\src\\HighlightingBase.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"deleted\"\n" +
                     "   revision=\"91677\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"79264\">\n" +
                     "<author>xvost</author>\n" +
                     "<date>2011-02-05T16:06:12.116814Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"src\\Daemon\\src\\HighlightingBase1.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   copied=\"true\"\n" +
                     "   item=\"added\">\n" +
                     "<commit\n" +
                     "   revision=\"79264\">\n" +
                     "<author>xvost</author>\n" +
                     "<date>2011-02-05T16:06:12.116814Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"src\\Decompiler.Core\">\n" +
                     "<wc-status\n" +
                     "   item=\"added\"\n" +
                     "   props=\"normal\"\n" +
                     "   copied=\"true\"\n" +
                     "   tree-conflicted=\"true\">\n" +
                     "<commit\n" +
                     "   revision=\"91559\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2012-01-18T19:17:40.876383Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"src\\Decompiler.Core\\Src\\Transformations\\Loops\\EliminateRedundantContinueTransformation.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   copied=\"true\"\n" +
                     "   item=\"modified\">\n" +
                     "<commit\n" +
                     "   revision=\"87972\">\n" +
                     "<author>Slava.Trenogin</author>\n" +
                     "<date>2011-10-06T21:27:41.539022Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"src\\Decompiler.Core\\Src\\Transformations\\StatementStructure\\ReduceReturnOrYieldBreakTransformation.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   props=\"normal\"\n" +
                     "   copied=\"true\">\n" +
                     "<commit\n" +
                     "   revision=\"87972\">\n" +
                     "<author>Slava.Trenogin</author>\n" +
                     "<date>2011-10-06T21:27:41.539022Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"stat.txt\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"unversioned\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\CommonServices\\src\\Services\\CommonServices.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"73708\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-10T14:14:04.090943Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Document.Tests\\AssemblyInfo.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"86795\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2011-09-11T13:54:16.917943Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Document.Tests\\RangeMarkerTest.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"77688\">\n" +
                     "<author>Alexander.Zverev</author>\n" +
                     "<date>2010-12-14T15:56:38.322018Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\AssemblyInfo.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"82127\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2011-04-13T20:57:30.828600Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\AssemblyNameInfo.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"90865\">\n" +
                     "<author>xvost</author>\n" +
                     "<date>2011-12-13T13:10:30.902950Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\BinaryBlobExtensions.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"74075\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-17T09:32:30.827654Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\BinaryBlobReader.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"74075\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-17T09:32:30.827654Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\BinaryBlobStream.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"74075\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-17T09:32:30.827654Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\BlobOnReader.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"74075\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-17T09:32:30.827654Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\ComStreamWrapper.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"91348\">\n" +
                     "<author>Sergey.Shkredov</author>\n" +
                     "<date>2012-01-09T11:26:53.770349Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\EmptyBlob.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"74075\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-09-17T09:32:30.827654Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\GAC.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"normal\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"75626\">\n" +
                     "<author>Slava.Trenogin</author>\n" +
                     "<date>2010-10-21T07:41:45.036722Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\GacUtil.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"91646\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2012-01-21T19:08:04.108471Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\IBinaryReader.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"71390\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-07-12T18:29:27.763006Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\IntInterval.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"55567\">\n" +
                     "<author>qx</author>\n" +
                     "<date>2009-06-03T10:11:14.985037Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\ManifestResourceUtil.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"87972\">\n" +
                     "<author>Slava.Trenogin</author>\n" +
                     "<date>2011-10-06T21:27:41.539022Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\MarshalSpecParser.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"76982\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-11-28T10:16:36.309593Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\MetadataHelpers.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"91348\">\n" +
                     "<author>Sergey.Shkredov</author>\n" +
                     "<date>2012-01-09T11:26:53.770349Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\ModuleQualificationUtil.cs\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\">\n" +
                     "<commit\n" +
                     "   revision=\"60918\">\n" +
                     "<author>xvost</author>\n" +
                     "<date>2009-11-03T10:42:37.363952Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\StreamBinaryReader.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"71390\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-07-12T18:29:27.763006Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\Resharper17short\\Platform\\src\\Metadata\\src\\Utils\\SubStream.cs\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"92564\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"76725\">\n" +
                     "<author>Leonid.Shalupov</author>\n" +
                     "<date>2010-11-20T13:23:44.172899Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "</target>\n" +
                     "<changelist\n" +
                     "   name=\"ads\">\n" +
                     "<entry\n" +
                     "   path=\"BuildVsix.cmd\">\n" +
                     "<wc-status\n" +
                     "   item=\"modified\"\n" +
                     "   revision=\"91677\"\n" +
                     "   props=\"none\">\n" +
                     "<commit\n" +
                     "   revision=\"77579\">\n" +
                     "<author>Victor.Kropp</author>\n" +
                     "<date>2010-12-13T11:06:36.141754Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "</changelist>\n" +
                     "</status>\n";

    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        handlerArr[0].getPending().getKind();
      }

      @Override
      public void switchChangeList(String newList) {
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        Assert.assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        Assert.assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          Assert.assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = changePathsIfNix(s);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);
    final MultiMap<String,PortableStatus> changes = handler.getCurrentListChanges();
  }

  private String changePathsIfNix(String s) {
    if (SystemInfo.isWindows) return s;
    s = FileUtil.toSystemIndependentName(s);
    return StringUtil.replace(s, "C:/", LINUX_ROOT);
  }

  private Info createStubInfo(final String basePath, final String baseUrl) throws SvnBindException {
    return new Info(basePath, createUrl(baseUrl), Revision.HEAD, NodeKind.FILE, "",
                    createUrl("http://a.b.c"), 1, new Date(), "me", null, Depth.EMPTY);
  }

  public void testStatusInExternalMove() throws Exception {
    final String status = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                          "<status>\n" +
                          "<target\n" +
                          "   path=\".\">\n" +
                          "<entry\n" +
                          "   path=\"slave\">\n" +
                          "<wc-status\n" +
                          "   item=\"external\"\n" +
                          "   props=\"none\">\n" +
                          "</wc-status>\n" +
                          "</entry>\n" +
                          "<entry\n" +
                          "   path=\"src\\com\\test\\just\">\n" +
                          "<wc-status\n" +
                          "   props=\"none\"\n" +
                          "   item=\"unversioned\">\n" +
                          "</wc-status>\n" +
                          "</entry>\n" +
                          "<entry\n" +
                          "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\MacMessagesParser.java\">\n" +
                          "<wc-status\n" +
                          "   item=\"added\"\n" +
                          "   props=\"none\"\n" +
                          "   copied=\"true\">\n" +
                          "<commit\n" +
                          "   revision=\"7\">\n" +
                          "<author>admin</author>\n" +
                          "<date>2012-09-12T12:16:51.621000Z</date>\n" +
                          "</commit>\n" +
                          "</wc-status>\n" +
                          "</entry>\n" +
                          "<entry\n" +
                          "   path=\"C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\SomeOtherClass.java\">\n" +
                          "<wc-status\n" +
                          "   props=\"none\"\n" +
                          "   item=\"deleted\"\n" +
                          "   revision=\"7\">\n" +
                          "<commit\n" +
                          "   revision=\"7\">\n" +
                          "<author>admin</author>\n" +
                          "<date>2012-09-12T12:16:51.621000Z</date>\n" +
                          "</commit>\n" +
                          "</wc-status>\n" +
                          "</entry>\n" +
                          "</target>\n" +
                          "</status>";
    final String basePath = "C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main";
    Ref<SvnStatusHandler> handler = Ref.create();
    final File baseFile = new File(basePath);
    final SvnStatusHandler.ExternalDataCallback callback = CmdStatusClient.createStatusCallback(status1 -> {
      System.out.println(status1.getURL());
      if (new File(
        "C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\MacMessagesParser.java")
        .equals(status1.getFile())) {
        Assert.assertEquals("http://external/src/com/slave/MacMessagesParser.java", status1.getURL().toString());
      }
      if (new File("C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave\\src\\com\\slave\\SomeOtherClass.java")
        .equals(status1.getFile())) {
        Assert.assertEquals("http://external/src/com/slave/SomeOtherClass.java", status1.getURL().toString());
      }
    }, baseFile, createStubInfo(basePath, "http://mainurl/"), () -> handler.get().getPending());
    handler.set(new SvnStatusHandler(callback, baseFile, o -> {
      try {
        if (new File("C:\\TestProjects\\sortedProjects\\Subversion\\local2\\sep12main\\main\\slave").equals(o)) {
          return createStubInfo(o.getPath(), "http://external");
        }
        return createStubInfo(o.getPath(), "http://12345");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    }));
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(status.getBytes(CharsetToolkit.UTF8_CHARSET)), handler.get());
    final MultiMap<String, PortableStatus> changes = handler.get().getCurrentListChanges();
  }

  public void testStatusWithSwitched() throws Exception {
    final String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<status>\n" +
                     "<target\n" +
                     "   path=\".\">\n" +
                     "<entry\n" +
                     "   path=\"root\\source\\s1.txt\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   switched=\"true\"\n" +
                     "   item=\"normal\"\n" +
                     "   revision=\"5\">\n" +
                     "<commit\n" +
                     "   revision=\"4\">\n" +
                     "<author>Irina.Chernushina</author>\n" +
                     "<date>2013-02-18T13:14:24.391537Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "<entry\n" +
                     "   path=\"root\\target\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   switched=\"true\"\n" +
                     "   item=\"normal\"\n" +
                     "   revision=\"5\">\n" +
                     "<commit\n" +
                     "   revision=\"4\">\n" +
                     "<author>Irina.Chernushina</author>\n" +
                     "<date>2013-02-18T13:14:24.391537Z</date>\n" +
                     "</commit>\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "</target>\n" +
                     "</status>";

    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final Set<PortableStatus> statuses = new HashSet<>();
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        statuses.add(handlerArr[0].getPending());
        handlerArr[0].getPending().getKind();
      }

      @Override
      public void switchChangeList(String newList) {
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        Assert.assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        Assert.assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          Assert.assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = changePathsIfNix(s);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);

    final String[] expected = {"root\\source\\s1.txt", "root\\target"};
    for (int i = 0; i < expected.length; i++) {
      expected[i] = FileUtil.toSystemDependentName(expected[i]);
    }
    int cntMatched = 0;
    for (PortableStatus status : statuses) {
      Assert.assertTrue(status.isSwitched());
      final String path = FileUtil.toSystemDependentName(status.getPath());
      for (String s1 : expected) {
        if (s1.equals(path)) {
          ++ cntMatched;
          break;
        }
      }
    }
    Assert.assertEquals(2, cntMatched);
  }

  public void testOneFileInChangeListStatus() throws Exception {
    final String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<status>\n" +
                     "<target\n" +
                     "   path=\".\">\n" +
                     "</target>\n" +
                     "<changelist\n" +
                     "   name=\"target\">\n" +
                     "<entry\n" +
                     "   path=\"a.txt\">\n" +
                     "<wc-status\n" +
                     "   props=\"none\"\n" +
                     "   item=\"added\"\n" +
                     "   revision=\"-1\">\n" +
                     "</wc-status>\n" +
                     "</entry>\n" +
                     "</changelist>\n" +
                     "</status>";

    final SvnStatusHandler[] handlerArr = new SvnStatusHandler[1];
    final boolean isWindows = SystemInfo.isWindows;
    final String basePath = isWindows ? "C:/base/" : "/base33729/";
    final Set<PortableStatus> statuses = new HashSet<>();
    final String[] clName = new String[1];
    final SvnStatusHandler handler = new
      SvnStatusHandler(new SvnStatusHandler.ExternalDataCallback() {
      @Override
      public void switchPath() {
        final PortableStatus pending = handlerArr[0].getPending();
        pending.setChangelistName(clName[0]);
        statuses.add(pending);
        pending.getKind();
      }

      @Override
      public void switchChangeList(String newList) {
        clName[0] = newList;
      }
    }, new File(basePath), o -> {
      try {
        o.getCanonicalFile();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isWindows) {
        final int idx = o.getPath().indexOf(":");
        Assert.assertTrue(idx > 0);
        final int secondIdx = o.getPath().indexOf(":", idx + 1);
        Assert.assertTrue(o.getPath(), secondIdx == -1);
      } else {
        if (o.getPath().contains(LINUX_ROOT)) {
          Assert.assertFalse(o.getPath().contains(basePath));
        }
      }
      try {
        return createStubInfo(basePath + "1", "http://a.b.c");
      }
      catch (SvnBindException e) {
        throw new RuntimeException(e);
      }
    });
    handlerArr[0] = handler;

    final String osChecked = changePathsIfNix(s);
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    parser.parse(new ByteArrayInputStream(osChecked.getBytes(CharsetToolkit.UTF8_CHARSET)), handler);

    Assert.assertEquals(1, statuses.size());
    final PortableStatus next = statuses.iterator().next();
    Assert.assertEquals("a.txt", next.getPath());
    Assert.assertEquals("target", next.getChangelistName());
  }
}
