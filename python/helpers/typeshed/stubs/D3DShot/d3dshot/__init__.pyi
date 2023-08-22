from d3dshot.capture_output import CaptureOutputs as CaptureOutputs
from d3dshot.d3dshot import D3DShot as D3DShot

pil_is_available: bool
numpy_is_available: bool
pytorch_is_available: bool
pytorch_gpu_is_available: bool
capture_output_mapping: dict[str, CaptureOutputs]
capture_outputs: list[str]

def determine_available_capture_outputs() -> list[CaptureOutputs]: ...
def create(capture_output: str = ..., frame_buffer_size: int = ...) -> D3DShot: ...
