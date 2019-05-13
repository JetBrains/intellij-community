__all__ = ['foo', 'bar']

for i in range(5):
    __all__.append('f' + str(i))
