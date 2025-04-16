from typing import Any

def my_func() -> Any:
    return object()

s = my_func()
s.<the_ref>documented_method()

from openstack import proxy
class Proxy(proxy.Proxy):
    def documented_method(self):
        """
        method documentation
        """
        return None
