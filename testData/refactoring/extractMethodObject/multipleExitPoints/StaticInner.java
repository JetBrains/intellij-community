class Test {
   void foo() {
      int i = 0;
      <selection>
      int j = i;
      int k = 0;
      </selection>
      int m = k + j;
   }
}