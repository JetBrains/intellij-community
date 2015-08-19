class ArgsTest:
    def __init__(self, key:str=None, value:str=None,
                 max_age=None, expires=None, path:str=None, domain:str=None,
                 secure:bool=False, httponly:bool=False, sync_expires:bool=True,
                 comment:str=None, version:int=None): pass

class Sub(ArgsTest):
    def __init__(self, key: str = None, value: str = None, max_age=None, expires=None, path: str = None,
                 domain: str = None, secure: bool = False, httponly: bool = False, sync_expires: bool = True,
                 comment: str = None, version: int = None):
        super().__init__(key, value, max_age, expires, path, domain, secure, httponly, sync_expires, comment, version)
