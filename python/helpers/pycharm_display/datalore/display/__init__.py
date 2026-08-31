from .display_log import debug


def display(data):
    from .display_ import display as _display
    return _display(data)


__all__ = ["display"]
