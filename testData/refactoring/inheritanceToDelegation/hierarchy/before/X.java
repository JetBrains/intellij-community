public class X extends Base {
    A myField;
    public void method(Test t) {
         myField = t.getA();
         myField.methodFromA();
         t.getA().methodFromA();
    }
}