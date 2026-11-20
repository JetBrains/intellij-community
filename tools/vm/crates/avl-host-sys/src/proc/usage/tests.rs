use super::*;

fn own_pid() -> i32 {
    i32::try_from(std::process::id()).expect("a pid")
}

/// The table holds this process with its parent, and its memory and its path answer.
#[test]
fn the_table_holds_this_process_with_its_parent_and_its_memory() {
    let pid = own_pid();
    let table = host_processes().expect("the table");
    let own = table.iter().find(|process| process.pid == pid).expect("this process");
    assert_eq!(own.parent_pid, i32::try_from(std::os::unix::process::parent_id()).expect("a pid"));
    assert!(own.responsible_pid.is_some(), "{own:?}");
    assert!(!own.name.is_empty(), "{own:?}");
    let memory = process_memory(pid).expect("the memory");
    assert!(memory.footprint_bytes > 0 && memory.resident_bytes > 0, "{memory:?}");
    let path = process_path(pid).expect("the path");
    let exe = std::env::current_exe().expect("the executable");
    assert_eq!(
        std::path::Path::new(&path).file_name(),
        exe.file_name(),
        "{path} against {}",
        exe.display()
    );
}

#[test]
fn a_pid_without_a_process_has_no_memory() {
    process_memory(0x7fff_fff0).expect_err("no memory");
    process_path(0x7fff_fff0).expect_err("no path");
}
