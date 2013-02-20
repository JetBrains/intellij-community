/*
 * Copyright 2006 ProductiveMe Inc.
 * Copyright 2013 JetBrains s.r.o.
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

package com.pme.exe.res.bmp;

import com.pme.exe.res.DirectoryEntry;
import com.pme.exe.res.RawResource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Date: May 3, 2006
 * Time: 1:00:08 PM
 */
public class PictureResourceInjector {
  public class PictureWrongFormat extends IOException {
    public PictureWrongFormat(File file) {
      super("Picture file is not found: " + file.getPath());
    }
  }

  public void inject(File file, DirectoryEntry root, String resourceId) throws IOException {
    byte[] bytes = new byte[(int) file.length()];
    RandomAccessFile stream = null;
    try {
      stream = new RandomAccessFile(file, "r");
      stream.read(bytes);
    }
    catch (IOException exception) {
      throw new PictureWrongFormat(file);
    }
    finally {
      if (stream != null) {
        stream.close();
      }
    }
    DirectoryEntry subDirBmp = root.findSubDir("IRD2").findSubDir(resourceId);
    RawResource bmpRes = subDirBmp.getRawResource(0);
    bmpRes.setBytes(bytes);
  }

}
