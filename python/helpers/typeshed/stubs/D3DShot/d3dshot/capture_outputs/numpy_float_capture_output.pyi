from d3dshot.capture_outputs.numpy_capture_output import NumpyCaptureOutput

# TODO: Once we can import non-types dependencies, this CaptureOutput should be float based
# See: #5768
class NumpyFloatCaptureOutput(NumpyCaptureOutput): ...
