//method
int foo(int a) {
  return switch(a) {
    case 1: {
      int x = 1;
      System.out.println(x);
      yield 1;
    }

    case 2: {
      int x = 2;
      System.out.println(x);
      yield 2;
    }

    case 3: {
        System.out.println(3);
    }
  }
}