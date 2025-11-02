import sys


def find_cached_module(mod_name):
    return sys.modules.get(mod_name, None)


def find_mod_attr(mod_name, attr):
    mod = find_cached_module(mod_name)
    if mod is None:
        return None
    return getattr(mod, attr, None)


def find_class_name(val):
    class_name = str(val.__class__)
    if class_name.find(".") != -1:
        class_name = class_name.split(".")[-1]

    elif class_name.find("'") != -1:  # does not have '.' (could be something like <type 'int'>)
        class_name = class_name[class_name.index("'") + 1 :]

    if class_name.endswith("'>"):
        class_name = class_name[:-2]

    return class_name


def get_contents_debug_adapter_protocol_container(resolver, value, fmt):
    dct = resolver.get_dictionary(value)
    lst = sorted(dct.items(), key=lambda tup: sorted_attributes_key(tup[0]))
    def evaluate_name(key):
        if not key.startswith("[") and not key.endswith("]"):
            return f".{key}"
        else:
            return key
    lst = [(key, value, evaluate_name(key)) for (key, value) in lst]
    return lst


def sorted_attributes_key(attr_name):
    if attr_name.startswith("__"):
        if attr_name.endswith("__"):
            # __ double under before and after __
            return 3, attr_name
        else:
            # __ double under before
            return 2, attr_name
    elif attr_name.startswith("_"):
        # _ single under
        return 1, attr_name
    else:
        # Regular (Before anything)
        return 0, attr_name