from typing import Dict


class Key:
    def __call__(self, obj):
        pass


def foo(d: Dict[str, int]):
    print(sorted(d.items(), key=Key()))
