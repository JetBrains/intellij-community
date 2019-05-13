from a import main
from b import C

if __name__ == '__main__':
    main()
    c = C()
    c.f('foo')
    c.g('bar')