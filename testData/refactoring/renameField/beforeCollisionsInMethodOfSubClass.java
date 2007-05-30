class A {
   int <caret>fieldToBeRenamed;
}
   
class B extends A {
   int method(int newFieldName) {
        if(newFieldName == 0) {
                return fieldToBeRenamed;
        }
        else {
            int newFieldName = fieldToBeRenamed;
            return fieldToBeRenamed + newFieldName;
        }
   }
}

