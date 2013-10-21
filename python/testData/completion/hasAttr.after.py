class C:
    @classmethod
    def key_from(cls):
        if hasattr(cls, 'key_template'):
            return cls.key_template