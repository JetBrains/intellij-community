/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.remote;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.remote.PyRemotePathMapper.PyPathMappingType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Alexander Koshevoy
 */
public class PyRemotePathMapperTest {
  @Test
  public void testFromSettings() {
    PathMappingSettings settings = new PathMappingSettings();
    PyRemotePathMapper emptyMapper = PyRemotePathMapper.fromSettings(settings, PyPathMappingType.USER_DEFINED);

    assertTrue(emptyMapper.isEmpty());

    settings.add(new PathMappingSettings.PathMapping("C:\\local\\folder", "/remote/folder"));
    settings.add(new PathMappingSettings.PathMapping("C:\\local\\folder\\child", "/remote/child-from-local"));
    settings.add(new PathMappingSettings.PathMapping("C:\\local\\child-from-remote", "/remote/folder/child"));
    PyRemotePathMapper mapper = PyRemotePathMapper.fromSettings(settings, PyPathMappingType.HELPERS);

    assertFalse(mapper.isEmpty());
    assertConvertsOneToAnother(mapper, "C:\\local\\folder\\src\\main.py", "/remote/folder/src/main.py");
    assertConvertsOneToAnother(mapper, "C:\\local\\folder\\child\\test\\all.py", "/remote/child-from-local/test/all.py");
    assertConvertsOneToAnother(mapper, "C:/local/child-from-remote/README", "/remote/folder/child/README");

    assertEquals("/unmapped/list.txt", mapper.convertToLocal("/unmapped/list.txt"));
    assertEquals("C:\\unmapped\\list.txt", mapper.convertToRemote("C:\\unmapped\\list.txt"));
  }

  @Test
  public void testReplicatedFolderInsideSysPath() {
    PyRemotePathMapper mapper = new PyRemotePathMapper();
    mapper.addMapping("C:\\Users\\J.S.\\.PyCharm\\system\\remote_sources\\-114", "/development/lib", PyPathMappingType.SYS_PATH);
    mapper.addMapping("C:\\Users\\J.S.\\.PyCharm\\system\\remote_sources\\-27315", "/development/src/project/module",
                      PyPathMappingType.SYS_PATH);

    assertFalse(mapper.isEmpty());
    assertEquals("C:/Users/J.S./.PyCharm/system/remote_sources/-27315/main.py",
                 mapper.convertToLocal("/development/src/project/module/main.py"));

    // although remote path specified here is shorter than previously added sys.path mapping this mapping has higher priority
    mapper.addMapping("C:\\Users\\J.S.\\development\\src\\project", "/development/src/project", PyPathMappingType.REPLICATED_FOLDER);

    assertConvertsOneToAnother(mapper, "C:\\Users\\J.S.\\development\\src\\project", "/development/src/project");
    assertConvertsOneToAnother(mapper, "C:/Users/J.S./development/src/project/module/main.py", "/development/src/project/module/main.py");
    assertConvertsOneToAnother(mapper, "C:/Users/J.S./.PyCharm/system/remote_sources/-114/json/decoder.py",
                               "/development/lib/json/decoder.py");

    assertConvertsOneToAnother(mapper, "C:/Users/J.S./.PyCharm/system/remote_sources/-114/json/decoder.py",
                               "/development/lib/json/decoder.py");
  }

  private static void assertConvertsOneToAnother(PathMapper mapper, String localPath, String remotePath) {
    assertEquals(FileUtil.toSystemIndependentName(remotePath), mapper.convertToRemote(localPath));
    assertEquals(FileUtil.toSystemIndependentName(localPath), mapper.convertToLocal(remotePath));
  }
}
