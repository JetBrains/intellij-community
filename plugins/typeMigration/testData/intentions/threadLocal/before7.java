// "Convert to ThreadLocal" "true"
class X {
  private final byte[] <caret>bytes = new byte[10];

  byte foo(byte b) {
    bytes[0] = 1;
    foo(bytes[1])
    return bytes[2];
  }
}