import java.util.*;

class A {
    Runnable method() { }
}

class Q implements List, Runnable  {
}

class Z implements List {
}

class B extends A {
    Q method() { }
}

class C extends A {
    Runnable method() { }
}