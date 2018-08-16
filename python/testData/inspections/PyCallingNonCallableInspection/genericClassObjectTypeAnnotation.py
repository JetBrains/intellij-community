from typing import TypeVar, Type


class User:
    pass


U = TypeVar('U', bound=User)


def new_user(user_class):
    # type: (Type[U]) -> U
    return user_class()
