class A {
   int newFieldName;
}
   
class B extends A {
   int method(int newFieldName) {
        if(newFieldName == 0) {
                return this.newFieldName;
        }
        else {
            int newFieldName = this.newFieldName;
            return this.newFieldName + newFieldName;
        }
   }
}

