class MenuItem(dict):
    def __new__(cls, *args, **kwargs):
        return super(MenuItem, cls).__new__(cls, *args, **kwargs)