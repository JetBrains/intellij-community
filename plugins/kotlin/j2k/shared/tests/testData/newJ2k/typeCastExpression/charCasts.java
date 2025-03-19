class A {
    void charToInt() {
        int a = 'a';
        char b = 'b';
        int c = b;
        int d = b + c;
    }

    void charToByte() {
        byte a = 'a';
        char b = 'b';
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void charToShort() {
        short a = 'a';
        char b = 'b';
        short c = (short) b;
        short d = (short) (b + c);
    }

    void charToLong() {
        long a = 'a';
        char b = 'b';
        long c = b;
        long d = b + c;
    }

    void charToFloat() {
        float a = 'a';
        char b = 'b';
        float c = b;
        float d = b + c;
    }

    void charToDouble() {
        double a = 'a';
        char b = 'b';
        double c = b;
        double d = b + c;
    }

    void intToChar() {
        char a = 1;
        int b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void byteToChar() {
        char a = (byte) 1;
        byte b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void shortToChar() {
        char a = (short) 1;
        short b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void longToChar() {
        char a = (char) 1L;
        long b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void floatToChar() {
        char a = (char) 1.0f;
        float b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void doubleToChar() {
        char a = (char) 1.0;
        double b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }
}