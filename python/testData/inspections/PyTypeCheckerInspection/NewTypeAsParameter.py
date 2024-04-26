from typing import NewType

UserId = NewType("UserId", int)

def get_user(user: UserId) -> str:
    pass


get_user(UserId(5))
get_user(<warning descr="Expected type 'UserId', got 'str' instead">"John"</warning>)
get_user(<warning descr="Expected type 'UserId', got 'int' instead">4</warning>)