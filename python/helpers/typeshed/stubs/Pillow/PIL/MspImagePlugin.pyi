from .ImageFile import ImageFile, PyDecoder

class MspImageFile(ImageFile):
    format: str
    format_description: str

class MspDecoder(PyDecoder):
    def decode(self, buffer): ...
