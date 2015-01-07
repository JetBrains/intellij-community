class EtagSupport(object):
    pass


class LockableItem(EtagSupport):
    pass


class Resource(LockableItem, _Unresolved):
    pass


class CopyContainer(_Unresolved):
    pass


class Navigation(_Unresolved):
    pass


class Tabs(_Unresolved):
    pass


class Collection(Resource):
    pass


class Traversable(object):
    pass


class ObjectManager(CopyContainer, Navigation, Tabs, _Unresolved, _Unresolved, Collection, Traversable):
    pass
