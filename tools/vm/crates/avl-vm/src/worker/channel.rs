//! The guest channels that are not a hypervisor exec. [`control_port::ControlPortChannel`] reaches the testing-ui
//! container through the control port of its guest server.

pub(crate) mod control_port;

pub(crate) use control_port::ControlPortChannel;
