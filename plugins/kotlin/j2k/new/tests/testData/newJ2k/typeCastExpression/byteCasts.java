class A {
    void byteToInt() {
        int a = (byte) 1;
        byte b = 2;
        int c = b;
        int d = b + c;
    }

    void byteToChar() {
        char a = (byte) 1;
        byte b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void byteToShort() {
        short a = (byte) 1;
        byte b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void byteToLong() {
        long a = (byte) 1;
        byte b = 2;
        long c = b;
        long d = b + c;
    }

    void byteToFloat() {
        float a = (byte) 1;
        byte b = 2;
        float c = b;
        float d = b + c;
    }

    void byteToDouble() {
        double a = (byte) 1;
        byte b = 2;
        double c = b;
        double d = b + c;
    }

    void intToByte() {
        byte a = 1;
        int b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void charToByte() {
        byte a = 'a';
        char b = 'b';
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void shortToByte() {
        byte a = (short) 1;
        short b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void longToByte() {
        byte a = (byte) 1L;
        long b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void floatToByte() {
        byte a = (byte) 1.0f;
        float b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void doubleToByte() {
        byte a = (byte) 1.0;
        double b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }
}