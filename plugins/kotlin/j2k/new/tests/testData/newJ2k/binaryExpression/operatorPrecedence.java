class J {
    public static void main(String[] args) {
        System.out.println(0x1234 & 0x1234 >>> 1); // 16
        System.out.println(1 | 2 << 3); // 17
        System.out.println(1 << 2 | 3); // 7
        System.out.println(1 << 2 | 3 >>> 4 & 5); // 4
        System.out.println(1 | 2 << 3 & 4 >>> 5); // 1
        System.out.println(1 | 2 << 3 & 4 >>> 5 | 6 | 7 & 8); // 7
        System.out.println(1 | 2 & 3 << 4 >>> 5 | 6 | 7 & 8); // 7
        System.out.println(1 | (2 & ((3 << 4) >>> 5)) | 6 | (7 & 8)); // 7
        System.out.println(1 | (2 << 3)); // 17
        System.out.println(5 << 16 | 0 >> 8 | 1); // 327681
        System.out.println(5 | 16 << 0 | 8 << 1); // 21
        System.out.println(2 | 1 & 5); // 3
        System.out.println((2 | 1) & 5); // 1
        System.out.println(false | true & 5 == 5 >>> 7);
        System.out.println(false | true & 5 >>> 5 == 7);
        System.out.println(false | 5 >>> 5 == 7 & true);
        System.out.println(true & 4 >= 5 >>> 7 | false);
        System.out.println(true & 4 >= 5 == true | false);
        System.out.println(true & true == 5 >= 4 | false);
    }
}