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

import java.io.*;

public class ClassFile {
  private int myMagic;
  private short myMajorVersion;
  private short myMinorVersion;
  private ConstantPoolInfo myConstantPool[];
  private short myAccessFlags;
  private ConstantPoolInfo myThisClass;
  private ConstantPoolInfo mySuperClass;
  private ConstantPoolInfo myInterfaces[];
  private FieldInfo myFields[];
  private MethodInfo myMethods[];
  private AttributeInfo myAttributes[];

  private void readConstantPool(DataInputStream stream) throws IOException {
    myConstantPool = new ConstantPoolInfo[stream.readShort()];
    myConstantPool[0] = new ConstantPoolInfo();
    for (int i = 1; i < myConstantPool.length; i++) {
      myConstantPool[i] = new ConstantPoolInfo();
      myConstantPool[i].read(stream);
      // These two types take up "two" spots in the table
      if ((myConstantPool[i].getType() == ConstantPoolInfo.LONG) ||
          (myConstantPool[i].getType() == ConstantPoolInfo.DOUBLE))
        i++;
    }
  }

  private void readInterfaces(DataInputStream stream) throws IOException {
    int count = stream.readShort();
    if (count != 0) {
      myInterfaces = new ConstantPoolInfo[count];
      for (int i = 0; i < count; i++) {
        int index = stream.readShort();
        if ((index < 1) || (index > myConstantPool.length - 1))
          throw new InvalidClassException("Wrong count for constant pool");
        myInterfaces[i] = myConstantPool[index];
      }
    }
  }

  private void readFields(DataInputStream stream) throws IOException {
    int count = stream.readShort();
    if (count != 0) {
      myFields = new FieldInfo[count];
      for (int i = 0; i < count; i++) {
        myFields[i] = new FieldInfo();
        myFields[i].read(stream, myConstantPool);
      }
    }
  }

  private void readMethods(DataInputStream stream) throws IOException {
    int count = stream.readShort();
    if (count != 0) {
      myMethods = new MethodInfo[count];
      for (int i = 0; i < count; i++) {
        myMethods[i] = new MethodInfo();
        myMethods[i].read(stream, myConstantPool);
      }
    }
  }

  private void readAttributes(DataInputStream stream) throws IOException {
    int count = stream.readShort();
    if (count != 0) {
      myAttributes = new AttributeInfo[count];
      for (int i = 0; i < count; i++) {
        myAttributes[i] = new AttributeInfo();
        myAttributes[i].read(stream, myConstantPool);
      }
    }
  }

  public static String readClassName(File file) throws IOException {
    FileInputStream stream = new FileInputStream(file);
    ClassFile classFile = new ClassFile();
    try {
      classFile.read(stream);
      return classFile.getSourceName();
    } finally{
      stream.close();
    }
  }

  public void read(InputStream in) throws IOException {
    DataInputStream stream = new DataInputStream(in);
    myMagic = stream.readInt();
    if (myMagic != 0xCAFEBABE) {
      throw new InvalidClassException("There is no magic number");
    }

    myMajorVersion = stream.readShort();
    myMinorVersion = stream.readShort();

    readConstantPool(stream);
    myAccessFlags = stream.readShort();
    myThisClass = myConstantPool[stream.readShort()];
    mySuperClass = myConstantPool[stream.readShort()];
    readInterfaces(stream);
    readFields(stream);
    readMethods(stream);
    readAttributes(stream);
  }

  public String getSourceName() {
    if (myAttributes != null) {
      for (int i = 0; i < myAttributes.length; i++) {
        String attrName = myAttributes[i].getName().getStrValue();
        if ("SourceFile".compareTo(attrName) == 0) {
          try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(myAttributes[i].getData()));
            ConstantPoolInfo cpi = myConstantPool[dis.readShort()];
            return cpi.getStrValue();
          } catch (IOException e) {
          }
        }
      }
    }
    return null;
  }
}
