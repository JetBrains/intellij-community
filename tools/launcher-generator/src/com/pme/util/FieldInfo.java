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

package com.pme.util;

import java.io.DataInputStream;
import java.io.IOException;

public class FieldInfo {
  private short myAccessFlags;
  private ConstantPoolInfo myName;
  private ConstantPoolInfo mySignature;
  private AttributeInfo myAttributes[];

  public short getAccessFlags() {
    return myAccessFlags;
  }

  public ConstantPoolInfo getName() {
    return myName;
  }

  public ConstantPoolInfo getSignature() {
    return mySignature;
  }

  public AttributeInfo[] getAttributes() {
    return myAttributes;
  }

  public void read(DataInputStream stream, ConstantPoolInfo pool[]) throws IOException {
    myAccessFlags = stream.readShort();
    myName = pool[stream.readShort()];
    mySignature = pool[stream.readShort()];
    int count = stream.readShort();
    if (count != 0) {
      myAttributes = new AttributeInfo[count];
      for (int i = 0; i < count; i++) {
        myAttributes[i] = new AttributeInfo();
        myAttributes[i].read(stream, pool);
      }
    }
  }
}
