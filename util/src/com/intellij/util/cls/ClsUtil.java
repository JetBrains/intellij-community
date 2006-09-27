/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.cls;



public class ClsUtil {
  public final static int MAGIC = 0xCAFEBABE;

  public final static int CONSTANT_Class = 7;
  public final static int CONSTANT_Fieldref = 9;
  public final static int CONSTANT_Methodref = 10;
  public final static int CONSTANT_InterfaceMethodref = 11;
  public final static int CONSTANT_String = 8;
  public final static int CONSTANT_Integer = 3;
  public final static int CONSTANT_Float = 4;
  public final static int CONSTANT_Long = 5;
  public final static int CONSTANT_Double = 6;
  public final static int CONSTANT_NameAndType = 12;
  public final static int CONSTANT_Utf8 = 1;

  public final static int ACC_PUBLIC = 0x0001;
  public final static int ACC_PRIVATE = 0x0002;
  public final static int ACC_PROTECTED = 0x0004;
  public final static int ACC_STATIC = 0x0008;
  public final static int ACC_FINAL = 0x0010;
  public final static int ACC_SYNCHRONIZED = 0x0020;
  public final static int ACC_BRIDGE = 0x0040;
  public final static int ACC_VARARGS = 0x0080;
  public final static int ACC_VOLATILE = 0x0040;
  public final static int ACC_TRANSIENT = 0x0080;
  public final static int ACC_NATIVE = 0x0100;
  public final static int ACC_INTERFACE = 0x0200;
  public final static int ACC_ABSTRACT = 0x0400;
  public final static int ACC_SYNTHETIC = 0x1000;
  public final static int ACC_ANNOTATION = 0x2000;
  public static final int ACC_ENUM = 0x4000;

  /**
   * Mask of access_flags that are meaningful in .class file format (VM Spec 4.1)
   */
  public final static int ACC_CLASS_MASK = ACC_PUBLIC | ACC_FINAL | ACC_INTERFACE | ACC_ABSTRACT | ACC_STATIC | ACC_ANNOTATION;

  public final static int ACC_FIELD_MASK = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC | ACC_FINAL | ACC_VOLATILE | ACC_TRANSIENT;
  public final static int ACC_METHOD_MASK = ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED | ACC_BRIDGE | ACC_VARARGS | ACC_NATIVE | ACC_ABSTRACT;
  private static final int POSITIVE_INFINITY_AS_INT = 0x7f800000;
  private static final long POSITIVE_INFINITY_AS_LONG = 0x7f80000000000000L;
  private static final int NEGATIVE_INFINITY_AS_INT = 0xff800000;
  private static final long NEGATIVE_INFINITY_AS_LONG = 0xff80000000000000L;

  public static int readU1(BytePointer ptr) throws ClsFormatException {
    if (ptr.offset < 0 || ptr.offset >= ptr.bytes.length) {
      throw new ClsFormatException();
    }
    return ptr.bytes[ptr.offset++] & 0xFF;
  }

  public static int readU2(BytePointer ptr) throws ClsFormatException {
    int b1 = readU1(ptr);
    int b2 = readU1(ptr);
    return (b1 << 8) + b2;
  }

  public static int readU4(BytePointer ptr) throws ClsFormatException {
    int b1 = readU1(ptr);
    int b2 = readU1(ptr);
    int b3 = readU1(ptr);
    int b4 = readU1(ptr);
    return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
  }

  public static long readU8(BytePointer ptr) throws ClsFormatException {
    long b1 = readU1(ptr);
    long b2 = readU1(ptr);
    long b3 = readU1(ptr);
    long b4 = readU1(ptr);
    long b5 = readU1(ptr);
    long b6 = readU1(ptr);
    long b7 = readU1(ptr);
    long b8 = readU1(ptr);

    return (b1 << 56) + (b2 << 48) + (b3 << 40) + (b4 << 32) + (b5 << 24) + (b6 << 16) + (b7 << 8) + b8;
  }

  public static String readUtf8Info(BytePointer ptr) throws ClsFormatException {
    return readUtf8Info(ptr, -1, -1);
  }

  public static String readUtf8Info(BytePointer ptr, int oldChar, int newChar) throws ClsFormatException {
    int tag = readU1(ptr);
    if (tag != CONSTANT_Utf8) {
      throw new ClsFormatException();
    }
    int length = readU2(ptr);
    return readUtf8(ptr.bytes, ptr.offset, ptr.offset + length, oldChar, newChar);
  }

  public static String readUtf8Info(byte[] bytes, int startOffset) throws ClsFormatException {
    int offset = startOffset;
    if (offset + 3 > bytes.length) {
      throw new ClsFormatException();
    }
    int tag = bytes[offset++] & 0xFF;
    if (tag != CONSTANT_Utf8) {
      throw new ClsFormatException();
    }
    int b1 = bytes[offset++] & 0xFF;
    int b2 = bytes[offset++] & 0xFF;
    int length = (b1 << 8) + b2;
    if (offset + length > bytes.length) {
      throw new ClsFormatException();
    }
    return readUtf8(bytes, offset, offset + length);
  }

  public static String readUtf8(byte[] bytes, int startOffset, int endOffset) throws ClsFormatException {
    return readUtf8(bytes, startOffset, endOffset, -1, -1);
  }

  public static String readUtf8(byte[] bytes, int startOffset, int endOffset, int oldChar, int newChar) throws ClsFormatException {
    char[] buffer = new char[endOffset - startOffset];
    int bOffset = 0;
    int offset = startOffset;
    while (offset < endOffset) {
      int b = bytes[offset++] & 0xFF;
      if (b == 0 || b >= 0xF0) {
        throw new ClsFormatException();
      }
      if (b < 0x80) {
        buffer[bOffset++] = (char) (b == oldChar ? newChar : b);
      } else {
        if (offset == endOffset) {
          throw new ClsFormatException();
        }
        int b1 = bytes[offset++] & 0xFF;
        if ((b & 0x20) == 0) {
          buffer[bOffset++] = (char) (((b & 0x1F) << 6) + (b1 & 0x3F));
        } else {
          if (offset == endOffset) {
            throw new ClsFormatException();
          }
          int b2 = bytes[offset++] & 0xFF;
          buffer[bOffset++] = (char) (((b & 0xF) << 12) + ((b1 & 0x3F) << 6) + (b2 & 0x3F));
        }
      }
    }
    return new String(buffer, 0, bOffset);
  }

  public static double readDouble(BytePointer ptr) throws ClsFormatException {
    int high = readU4(ptr);
    int low = readU4(ptr);
    long longValue = ((long) high << 32) + low;
    double doubleValue;
    if (longValue == POSITIVE_INFINITY_AS_LONG) {
      doubleValue = Double.POSITIVE_INFINITY;
    } else if (longValue == NEGATIVE_INFINITY_AS_LONG) {
      doubleValue = Double.NEGATIVE_INFINITY;
    } else if (0x7ff0000000000001L <= longValue && longValue <= 0x7fffffffffffffffL) {
      doubleValue = Double.NaN;
    } else if (0xfff0000000000001L <= longValue && longValue <= 0xffffffffffffffffL) {
      doubleValue = Double.NaN;
    } else {
      int s = ((longValue >> 63) == 0) ? 1 : -1;
      int e = (int) ((longValue >> 52) & 0x7ffL);
      long m = (e == 0) ? ((longValue & 0xfffffffffffffL) << 1) : ((longValue & 0xfffffffffffffL) | 0x10000000000000L);
      doubleValue = s * m * Math.pow(2, e - 1075);
    }
    return doubleValue;
  }

  public static float readFloat(BytePointer ptr) throws ClsFormatException {
    int value = readU4(ptr);
    float floatValue;
    if (value == POSITIVE_INFINITY_AS_INT) {
      floatValue = Float.POSITIVE_INFINITY;
    } else if (value == NEGATIVE_INFINITY_AS_INT) {
      floatValue = Float.NEGATIVE_INFINITY;
    } else if ((value >= 0x7f800001 && value <= 0x7fffffff) || (value >= 0xff800001 && value <= 0xffffffff)) {
      floatValue = Float.NaN;
    } else {
      int s = ((value >> 31) == 0) ? 1 : -1;
      int e = ((value >> 23) & 0xff);
      int m = (e == 0) ? (value & 0x7fffff) << 1 : (value & 0x7fffff) | 0x800000;
      floatValue = (float) (s * m * Math.pow(2, e - 150));
    }
    return floatValue;
  }

  public static void skipAttribute(BytePointer ptr) throws ClsFormatException {
    ptr.offset += 2;
    int length = readU4(ptr);
    ptr.offset += length;
  }

  public static void skipAttributes(BytePointer ptr) throws ClsFormatException {
    int count = readU2(ptr);
    for (int i = 0; i < count; i++) {
      skipAttribute(ptr);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getTypeText(byte[] data, int offset) throws ClsFormatException {
    int count = 0;
    while (true) {
      if (offset >= data.length) {
        throw new ClsFormatException();
      }
      if (data[offset] != '[') break;
      offset++;
      count++;
    }

    String text;
    switch ((char) data[offset]) {
      default:
        throw new ClsFormatException();

      case 'B':
        text = "byte";
        break;

      case 'C':
        text = "char";
        break;

      case 'D':
        text = "double";
        break;

      case 'F':
        text = "float";
        break;

      case 'I':
        text = "int";
        break;

      case 'J':
        text = "long";
        break;

      case 'S':
        text = "short";
        break;

      case 'Z':
        text = "boolean";
        break;

      case 'V':
        text = "void";
        break;

      case 'L':
        int offset1 = offset + 1;
        while (true) {
          if (offset1 >= data.length) {
            throw new ClsFormatException();
          }
          if (data[offset1] == ';') break;
          offset1++;
        }
        String className = readUtf8(data, offset + 1, offset1, '/', '.');
        text = convertClassName(className, false);
        break;
    }

    for (int i = 0; i < count; i++) {
      text += "[]";
    }

    return text;
  }

  public static int getTypeEndOffset(byte[] data, int startOffset) throws ClsFormatException {
    int offset = startOffset;
    while (true) {
      if (offset >= data.length) {
        throw new ClsFormatException();
      }
      if (data[offset] != '[') break;
      offset++;
    }
    if (data[offset++] == 'L') {
      while (true) {
        if (offset >= data.length) {
          throw new ClsFormatException();
        }
        if (data[offset++] == ';') break;
      }
    }
    return offset;
  }

  public static String convertClassName(String internalClassName, boolean convertSlashesToDots) {
    String className = convertSlashesToDots ? internalClassName.replace('/', '.') : internalClassName;
    int index = className.indexOf('$');
    if (index < 0) return className;

    StringBuffer buffer = new StringBuffer(className);
    while(true){
      if (className.length() == index + 1) break;
      char c = className.charAt(index + 1);
      if (Character.isJavaIdentifierStart(c)){
        buffer.setCharAt(index, '.');
      }
      index = className.indexOf('$', index + 1);
      if (index < 0) break;
    }
    return buffer.toString();
  }

  public static boolean isPublic(int flags) {
    return (ACC_PUBLIC & flags) != 0;
  }

  public static boolean isProtected(int flags) {
    return (ACC_PROTECTED & flags) != 0;
  }

  public static boolean isPackageLocal(int flags) {
    return !isPublic(flags) && !isProtected(flags) && !isPrivate(flags);
  }

  public static boolean isPrivate(int flags) {
    return (ACC_PRIVATE & flags) != 0;
  }

  public static boolean isAbstract(int flags) {
    return (ACC_ABSTRACT & flags) != 0;
  }

  public static boolean isBridge(int flags) {
    return (ACC_BRIDGE & flags) != 0;
  }

  public static boolean isSynthetic(int flags) {
    return (ACC_SYNTHETIC & flags) != 0;
  }

  public static boolean isAnnotation(int flags) {
    return (ACC_ANNOTATION & flags) != 0;
  }

  public static boolean isFinal(int flags) {
    return (ACC_FINAL & flags) != 0;
  }

  public static boolean isStatic(int flags) {
    return (ACC_STATIC & flags) != 0;
  }
}
