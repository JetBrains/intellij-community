// "Convert to ThreadLocal" "true"
class X {
    private final ThreadLocal<byte[]> bytes = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[10];
        }
    };

  byte foo(byte b) {
    bytes.get()[0] = 1;
    foo(bytes.get()[1])
    return bytes.get()[2];
  }
}