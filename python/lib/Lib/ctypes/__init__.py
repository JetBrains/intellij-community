import jffi

__version__ = "0.0.1"

_TypeMap = {
    'b': jffi.Type.BYTE,
    'B': jffi.Type.UBYTE,
    'h': jffi.Type.SHORT,
    'H': jffi.Type.USHORT,
    'i': jffi.Type.INT,
    'I': jffi.Type.UINT,
    'l': jffi.Type.LONG,
    'L': jffi.Type.ULONG,
    'q': jffi.Type.LONGLONG,
    'Q': jffi.Type.ULONGLONG,
    'f': jffi.Type.FLOAT,
    'd': jffi.Type.DOUBLE,
    '?': jffi.Type.BOOL,
    'z': jffi.Type.STRING,
    'P': jffi.Type.POINTER
}

class _CTypeMetaClass(type):

    def __new__(cls, name, bases, dict):
        return type.__new__(cls, name, bases, dict)

    def __mul__(self, len):
        dict = { '_jffi_type': jffi.Type.Array(self, len) }

        # Look back up the stack frame to find out the module this new type is declared in
        import inspect
        mod = inspect.getmodule(inspect.stack()[1][0])
        if mod is None:
            name = "__main__"
        else:
            name = mod.__name__
        dict["__module__"] = name
        return type("%s_Array_%d" % (self.__name__, len), (jffi.ArrayCData, _ArrayCData, _CData), dict)

class _CData(object):
    @classmethod
    def in_dll(self, lib, name):
        return self.from_address(lib[name])

    @classmethod
    def size(self):
        return self._jffi_type.size()

class _ScalarCData(jffi.ScalarCData, _CData):
    __metaclass__ = _CTypeMetaClass

    
class _ArrayCData(object):
    def __len__(self):
        return self._jffi_type.length

class _StructLayoutBuilder(object):
    def __init__(self, union = False):
        self.size = 0
        self.offset = 0
        self.fields = []
        self.union = union

    def align(self, offset, align):
        return align + ((offset - 1) & ~(align - 1));

    def add_fields(self, fields):
        for f in fields:
            self.add_field(f)
        return self

    def add_field(self, f):
        if not issubclass(f[1], _ScalarCData):
            raise RuntimeError("non-scalar fields not supported")

        if len(f) != 2:
            raise RuntimeError("structs with bitfields not supported")

        self.offset = self.align(self.offset, alignment(f[1]))
        self.fields.append(jffi.StructLayout.ScalarField(f[0], f[1], self.offset))
        if not self.union:
            self.offset += sizeof(f[1])
        self.size = max(self.offset, sizeof(f[1]))

        return self

    def build(self):
        return jffi.StructLayout(fields = self.fields, union = self.union)

class _AggregateMetaClass(type):
    @staticmethod
    def __new_aggregate__(cls, name, bases, dict, union = False):
        if dict.has_key('_fields_'):
            layout = dict['_jffi_type'] = _StructLayoutBuilder(union).add_fields(dict['_fields_']).build()
            # make all fields accessible via .foo
            for f in dict['_fields_']:
                dict[f[0]] = layout[f[0]]
            dict['__fields_'] = dict['_fields_']
        else:
            dict['__fields_'] = []
        if dict.has_key('_pack_'):
            raise NotImplementedError("struct packing not implemented")
        if dict.has_key('_anonymous_'):
            raise NotImplementedError("anonymous fields not implemented")

        return type.__new__(cls, name, bases, dict)

    def get_fields(self):
        return self.__fields_

    def set_fields(self, fields):
        layout = _StructLayoutBuilder(union = issubclass(Union, self)).add_fields(fields).build()
        self.__fields_ = fields
        self._jffi_type = layout
        # make all fields accessible via .foo
        for f in fields:
            setattr(self, f[0], layout[f[0]])

    _fields_ = property(get_fields, set_fields)
    # Make _pack_ and _anonymous_ throw errors if anyone tries to use them
    _pack_ = property(None)
    _anonymous_ = property(None)

class _StructMetaClass(_AggregateMetaClass):
    def __new__(cls, name, bases, dict):
        return _AggregateMetaClass.__new_aggregate__(cls, name, bases, dict, union = False)

class _UnionMetaClass(_AggregateMetaClass):
    def __new__(cls, name, bases, dict):
        return _AggregateMetaClass.__new_aggregate__(cls, name, bases, dict, union = True)

class Structure(jffi.Structure, _CData):
    __metaclass__ = _StructMetaClass

class Union(jffi.Structure, _CData):
    __metaclass__ = _UnionMetaClass

def sizeof(type):
    if hasattr(type, '_jffi_type'):
        return type._jffi_type.size()
    else:
        raise TypeError("this type has no size")

def alignment(type):
    return type._jffi_type.alignment()

def addressof(cdata):
    return cdata.address()

def byref(cdata, offset = 0):
    return cdata.byref(offset)

def pointer(cdata):
    return cdata.pointer(POINTER(cdata.__class__))

memmove = jffi.memmove
memset = jffi.memset

_pointer_type_cache = {}
def POINTER(ctype):
    # If a pointer class for the C type has been created, re-use it
    if _pointer_type_cache.has_key(ctype):
        return _pointer_type_cache[ctype]

    # Create a new class for this particular C type
    dict = { '_jffi_type': jffi.Type.Pointer(ctype) }
    # Look back up the stack frame to find out the module this new type is declared in
    import inspect
    mod = inspect.getmodule(inspect.stack()[1][0])
    if mod is None:
        name = "__main__"
    else:
        name = mod.__name__
    dict["__module__"] = name

    ptype = type("LP_%s" % (ctype.__name__,), (jffi.PointerCData, _CData), dict)
    _pointer_type_cache[ctype] = ptype
    return ptype

class c_bool(_ScalarCData):
    _type_ = '?'
    _jffi_type = jffi.Type.BOOL

class c_byte(_ScalarCData):
    _type_ = 'b'
    _jffi_type = jffi.Type.BYTE

class c_ubyte(_ScalarCData):
    _type_ = 'B'
    _jffi_type = jffi.Type.UBYTE

class c_short(_ScalarCData):
    _type_ = 'h'
    _jffi_type = jffi.Type.SHORT

class c_ushort(_ScalarCData):
    _type_ = 'H'
    _jffi_type = jffi.Type.USHORT

class c_int(_ScalarCData):
    _type_ = 'i'
    _jffi_type = jffi.Type.INT

class c_uint(_ScalarCData):
    _type_ = 'I'
    _jffi_type = jffi.Type.UINT

class c_longlong(_ScalarCData):
    _type_ = 'q'
    _jffi_type = jffi.Type.LONGLONG

class c_ulonglong(_ScalarCData):
    _type_ = 'Q'
    _jffi_type = jffi.Type.ULONGLONG

class c_long(_ScalarCData):
    _type_ = 'l'
    _jffi_type = jffi.Type.LONG

class c_ulong(_ScalarCData):
    _type_ = 'L'
    _jffi_type = jffi.Type.ULONG

class c_float(_ScalarCData):
    _type_ = 'f'
    _jffi_type = jffi.Type.FLOAT

class c_double(_ScalarCData):
    _type_ = 'd'
    _jffi_type = jffi.Type.DOUBLE

c_int8 = c_byte
c_uint8 = c_ubyte
c_int16 = c_short
c_uint16 = c_ushort
c_int32 = c_int
c_uint32 = c_uint
c_int64 = c_longlong
c_uint64 = c_ulonglong

c_size_t = c_ulong
c_ssize_t = c_long

class c_char_p(jffi.StringCData, _CData):
    _type_ = 'z'
    _jffi_type = jffi.Type.STRING

class c_void_p(_ScalarCData):
    _type_ = 'P'
    _jffi_type = jffi.Type.POINTER

class _Function(jffi.Function):
    _restype = c_int
    _argtypes = None


class CDLL:
    DEFAULT_MODE = jffi.RTLD_GLOBAL | jffi.RTLD_LAZY

    def __init__(self, name, mode = DEFAULT_MODE, handle = None):
        self._handle = jffi.dlopen(name, mode)

    def __getattr__(self, name):
        if name.startswith('__') and name.endswith('__'):
            raise AttributeError, name
        func = self.__getitem__(name)
        setattr(self, name, func)
        return func

    def __getitem__(self, name):
        return _Function(self._handle.find_symbol(name))

class LibraryLoader(object):
    def __init__(self, dlltype):
        self._dlltype = dlltype

    def __getattr__(self, name):
        if name[0] == '_':
            raise AttributeError(name)
        dll = self._dlltype(name)
        setattr(self, name, dll)
        return dll

    def __getitem__(self, name):
        return getattr(self, name)

    def LoadLibrary(self, name):
        return self._dlltype(name)

cdll = LibraryLoader(CDLL)
