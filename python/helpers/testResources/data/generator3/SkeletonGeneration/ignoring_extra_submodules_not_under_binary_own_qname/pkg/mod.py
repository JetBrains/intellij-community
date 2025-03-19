import sys


class ProperSubmodule(object):
    attr = 42


ProperSubmodule.__name__ = 'pkg.mod.submodule'
sys.modules['pkg.mod.submodule'] = ProperSubmodule  # type: ignore
del ProperSubmodule


class SiblingModule(object):  # noqa
    attr = 42


SiblingModule.__name__ = 'pkg.unrelated'
sys.modules['pkg.unrelated'] = SiblingModule  # type: ignore
del SiblingModule


class UnrelatedModule(object):  # noqa
    attr = 42


UnrelatedModule.__name__ = 'unrelated'
sys.modules['unrelated'] = UnrelatedModule  # type: ignore
del UnrelatedModule

own_attribute = 42
