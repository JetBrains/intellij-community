This folder contains the utilities to attach a target process to the pydev debugger.

The main module to be called for the attach is:

attach_pydevd.py

it should be called as;

python attach_pydevd.py --port 5678 --pid 1234

Note that the client is responsible for having a remote debugger alive in the given port for the attach to work.