class Test {
   void m(boolean b) {
           int x = 42;
           try {

               x = newMethod(b, x);

           } catch(Exception e) {
               System.out.println(x);
           }
       }

    private int newMethod(boolean b, int x) throws Exception {
        if(b) {
            x = 23;
            throw new Exception();
        }
        return x;
    }

}