class Test {
    int method() {
         <selection>try {
             if(cond1) return 0;
             else if(cond2) return 1;
             System.out.println("Text");
         } finally {           
             doSomething();
         }</selection>
         return 12;
    }
}