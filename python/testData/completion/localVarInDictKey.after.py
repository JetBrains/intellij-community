ENCODINGS = {}

class C:
    def foo(self):
        if self.id[0] == 'T':
            encoding = ord(self.rawData[0])
            if 0 <= encoding < len(ENCODINGS):
                value = self.rawData[1:].decode(ENCODINGS[encoding])