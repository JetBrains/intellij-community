class A {
    void shortToInt() {
        int a = (short) 1;
        short b = 2;
        int c = b;
        int d = b + c;
    }

    void shortToChar() {
        char a = (short) 1;
        short b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void shortToByte() {
        byte a = (short) 1;
        short b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void shortToLong() {
        long a = (short) 1;
        short b = 2;
        long c = b;
        long d = b + c;
    }

    void shortToFloat() {
        float a = (short) 1;
        short b = 2;
        float c = b;
        float d = b + c;
    }

    void shortToDouble() {
        double a = (short) 1;
        short b = 2;
        double c = b;
        double d = b + c;
    }

    void intToShort() {
        short a = 1;
        int b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void charToShort() {
        short a = 'a';
        char b = 'b';
        short c = (short) b;
        short d = (short) (b + c);
    }

    void byteToShort() {
        short a = (byte) 1;
        byte b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void longToShort() {
        short a = (short) 1L;
        long b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void floatToShort() {
        short a = (short) 1.0f;
        float b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void doubleToShort() {
        short a = (short) 1.0;
        double b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }
}