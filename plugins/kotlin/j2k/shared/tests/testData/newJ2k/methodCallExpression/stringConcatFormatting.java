class A {
    public static void main(String[] args) {
        String s = "";

        String result = s
          .replace("_", "/")
          .concat("==");

        String result2 = s
          .replace("_", "/") // comment
          .concat("==");

        String result3 = s
          .replace("_", "/").concat("==");

        String result4 = s
          .replace("_", "/").concat("==").replace("_", "/");

        String result5 = s
          .replace("_", "/")
          .concat("==")
          .replace("_", "/");

        String result6 = s
          .replace("_", "/")
          .concat("==") // comment
          .replace("_", "/");
    }
}