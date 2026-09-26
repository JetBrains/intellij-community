from .display_log import debug


def display(data):
    from . import display_
    return display_.display(data)


__all__ = ["display"]
