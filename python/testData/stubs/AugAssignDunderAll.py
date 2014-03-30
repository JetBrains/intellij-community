__all__ = ['foo', 'bar']

for i in range(5):
    __all__ += 'f' + str(i)
