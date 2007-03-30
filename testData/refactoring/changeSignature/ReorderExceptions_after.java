class MyException extends Exception{
}

class MyException1 extends Exception{
}

class Test {
    void foo () throws MyException1, MyException {
    }

    void bar () {
        try {
            foo();
        }
        catch (MyException e) {}
        catch (MyException1 myException1) {}
    }
}

class Derived extends Test {
    void foo() throws MyException1, MyException {

    }

    void bar () {
        try {
            foo();
        }
        catch (MyException e) {}
        catch (MyException1 myException1) {}
    }
}