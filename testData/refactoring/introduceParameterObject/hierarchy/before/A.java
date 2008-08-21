class A extends Test{
   void foo(int i) {
     System.out.println(i++);
   }

   void bazz(){
     foo(0);
   }
}