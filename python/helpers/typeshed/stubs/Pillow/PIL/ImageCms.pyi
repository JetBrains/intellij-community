import sys
from _typeshed import Incomplete, Unused
from enum import IntEnum

from .Image import ImagePointHandler

DESCRIPTION: str
VERSION: str
core: Incomplete

class Intent(IntEnum):
    PERCEPTUAL = 0
    RELATIVE_COLORIMETRIC = 1
    SATURATION = 2
    ABSOLUTE_COLORIMETRIC = 3

class Direction(IntEnum):
    INPUT = 0
    OUTPUT = 1
    PROOF = 2

FLAGS: Incomplete

class ImageCmsProfile:
    def __init__(self, profile) -> None: ...
    def tobytes(self): ...

class ImageCmsTransform(ImagePointHandler):
    transform: Incomplete
    input_mode: Incomplete
    output_mode: Incomplete
    output_profile: Incomplete
    def __init__(
        self,
        input,
        output,
        input_mode,
        output_mode,
        intent=...,
        proof: Incomplete | None = None,
        proof_intent=...,
        flags: int = 0,
    ) -> None: ...
    def point(self, im): ...
    def apply(self, im, imOut: Incomplete | None = None): ...
    def apply_in_place(self, im): ...

if sys.platform == "win32":
    def get_display_profile(handle: Incomplete | None = None) -> ImageCmsProfile | None: ...

else:
    def get_display_profile(handle: Unused = None) -> None: ...

class PyCMSError(Exception): ...

def profileToProfile(
    im,
    inputProfile,
    outputProfile,
    renderingIntent=...,
    outputMode: Incomplete | None = None,
    inPlace: bool = False,
    flags: int = 0,
): ...
def getOpenProfile(profileFilename): ...
def buildTransform(inputProfile, outputProfile, inMode, outMode, renderingIntent=..., flags: int = 0): ...
def buildProofTransform(
    inputProfile, outputProfile, proofProfile, inMode, outMode, renderingIntent=..., proofRenderingIntent=..., flags=16384
): ...

buildTransformFromOpenProfiles = buildTransform
buildProofTransformFromOpenProfiles = buildProofTransform

def applyTransform(im, transform, inPlace: bool = False): ...
def createProfile(colorSpace, colorTemp: int = -1): ...
def getProfileName(profile): ...
def getProfileInfo(profile): ...
def getProfileCopyright(profile): ...
def getProfileManufacturer(profile): ...
def getProfileModel(profile): ...
def getProfileDescription(profile): ...
def getDefaultIntent(profile): ...
def isIntentSupported(profile, intent, direction): ...
def versions(): ...
