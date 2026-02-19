/*
 * This is the structure used to send call info to server.
 */
struct RequestHeader {
    1: string request_id
    2: string seq
    3: map<string, string> meta
}

struct ResponseHeader {
    1: map<string, string> meta
}

/**
 * This is the struct that a successful upgrade will reply with.
 */
struct UpgradeReply {
    1:i32 version
}

struct UpgradeArgs {
    1: string app_id
    2: i32 version
}