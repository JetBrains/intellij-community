from typing import Callable


def foo() -> Callable:
    pass


def bar() -> Callable[..., str]:
    pass


def baz() -> Callable[[int, str], str]:
    pass


cllbl_a = foo()
cllbl_a()
cllbl_a(1)
cllbl_a(1, "2")


cllbl_b = bar()
cllbl_b()
cllbl_b(1)
cllbl_b(1, "2")


cllbl_c = baz()
cllbl_c(1, "2")
cllbl_c(1, <warning descr="Expected type 'str', got 'int' instead">2</warning>)
cllbl_c(<warning descr="Expected type 'int', got 'str' instead">"1"</warning>, "2")
cllbl_c(<warning descr="Expected type 'int', got 'list[Any]' instead">[]</warning>, <warning descr="Expected type 'str', got 'list[Any]' instead">[]</warning>)
