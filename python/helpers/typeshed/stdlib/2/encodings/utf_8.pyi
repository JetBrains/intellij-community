import codecs

class IncrementalEncoder(codecs.IncrementalEncoder):
    pass
class IncrementalDecoder(codecs.BufferedIncrementalDecoder):
    pass
class StreamWriter(codecs.StreamWriter):
    pass
class StreamReader(codecs.StreamReader):
    pass

def getregentry() -> codecs.CodecInfo: ...
def encode(input: str, errors: str = ...) -> bytes: ...
def decode(input: bytes, errors: str = ...) -> str: ...
