import sys


def find_cached_module(mod_name):
    return sys.modules.get(mod_name, None)

def find_mod_attr(mod_name, attr):
    mod = find_cached_module(mod_name)
    if mod is None:
        return None
    return getattr(mod, attr, None)

