class Test {
  void foo(Param param) {
    bar(param.getS());
  }

  void bar(String s){}

    public class Param {
        private final String s;

        public Param(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }
    }
}