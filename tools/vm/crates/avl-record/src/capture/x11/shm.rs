//! One System V shared memory segment attached by both the recorder and the X server.

use anyhow::{Context, bail};
use x11rb::connection::{Connection, RequestConnection};
use x11rb::protocol::shm::{self, ConnectionExt as _};
use x11rb::protocol::xproto::ImageFormat;
use x11rb::rust_connection::RustConnection;

pub(super) struct Segment {
    seg: shm::Seg,
    address: *mut libc::c_void,
    size: usize,
}

// SAFETY: the mapping is owned by this value alone and only read through `&self` while the source's lock is held;
// the raw pointer is what keeps the auto traits off.
unsafe impl Send for Segment {}
// SAFETY: see `Send`; nothing writes through the pointer on this side.
unsafe impl Sync for Segment {}

impl Segment {
    /// Creates a private segment of `size` bytes and asks the X server to attach it too.
    ///
    /// The segment is marked for removal as soon as both sides are attached. From then on the kernel frees it when
    /// the last side detaches, so a recorder killed outright leaks no segment, which matters on a guest that runs
    /// hundreds of scenarios between boots.
    pub(super) fn attach(conn: &RustConnection, size: usize) -> anyhow::Result<Self> {
        if conn.extension_information(shm::X11_EXTENSION_NAME)?.is_none() {
            bail!("the X server offers no MIT-SHM");
        }
        // SAFETY: shmget takes no pointer; a failure is -1 with errno set.
        let id = unsafe { libc::shmget(libc::IPC_PRIVATE, size, libc::IPC_CREAT | 0o600) };
        if id < 0 {
            return Err(std::io::Error::last_os_error()).with_context(|| format!("cannot create a shared memory segment of {size} bytes"));
        }
        let remove = || {
            // SAFETY: IPC_RMID takes no buffer; marking a segment for removal leaves existing attachments valid.
            unsafe { libc::shmctl(id, libc::IPC_RMID, std::ptr::null_mut()) };
        };
        // SAFETY: a null address lets the kernel pick one; a failure is (void *)-1.
        let address = unsafe { libc::shmat(id, std::ptr::null(), 0) };
        if address as isize == -1 {
            let error = std::io::Error::last_os_error();
            remove();
            return Err(error).context("cannot attach the shared memory segment");
        }
        let mut segment = Self { seg: 0, address, size };
        let attached = conn.generate_id().map_err(anyhow::Error::from).and_then(|seg| {
            let id = u32::try_from(id)?;
            conn.shm_attach(seg, id, false)?
                .check()
                .context("the X server cannot attach the segment, so it does not share this machine's IPC namespace")?;
            Ok(seg)
        });
        remove();
        // A failure drops the segment, which detaches this side's mapping.
        segment.seg = attached?;
        Ok(segment)
    }

    /// Has the server write the root's pixels into the segment, and answers how many bytes it wrote.
    pub(super) fn get_image(&self, conn: &RustConnection, root: u32, width: u16, height: u16) -> anyhow::Result<u32> {
        let reply = conn
            .shm_get_image(root, 0, 0, width, height, !0, ImageFormat::Z_PIXMAP.into(), self.seg, 0)?
            .reply()?;
        Ok(reply.size)
    }

    /// Copies the segment's first bytes into `target`, which is no longer than the segment.
    ///
    /// The copy reads through a raw pointer and makes no `&[u8]` of the segment. The X server is another process
    /// that can write the segment at any time, so a shared reference would promise an immutability nobody keeps.
    pub(super) fn copy_to(&self, target: &mut [u8]) {
        let length = target.len().min(self.size);
        // SAFETY: the mapping is `size` bytes long and stays attached for this value's life, and `length` is at most
        // `size`. `target` is this process's own buffer of at least `length` bytes, so the two ranges do not overlap.
        // The server wrote the frame before its GetImage reply, which the caller waited for.
        unsafe { std::ptr::copy_nonoverlapping(self.address.cast::<u8>(), target.as_mut_ptr(), length) };
    }
}

impl Drop for Segment {
    /// Releases this side's mapping. The server's attachment goes with the connection, which is closed right after,
    /// so there is no round trip to wait on here, and none a server that stopped answering could hold.
    fn drop(&mut self) {
        // SAFETY: the address came from shmat and is detached exactly once.
        unsafe { libc::shmdt(self.address) };
    }
}
