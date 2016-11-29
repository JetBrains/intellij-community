namespace java com.jetbrains.python.profiler

struct FuncStat {
  1: required string file,       // File name of the executed function
  2: optional i32 line,    	  // Line number of the executed function
  3: required string func_name,	  // Name of the executed function
  4: required i32 calls_count,	  // number of times the executed function is called.
  5: required double total_time,	  // total time spent in the executed function. See Clock Types to interpret this value correctly.
  6: required double own_time,     // total time spent in the executed function, excluding subcalls. See Clock Types to interpret this value correctly.
}

struct Function {
   1: required FuncStat func_stat,
   2: required list<FuncStat> callers,    // list of functions called from the executed function.
}


struct Stats {
  1: required list<Function> func_stats
}

struct CallTreeStat {
  1: required i32 count,
  2: required string name,
  3: required list<CallTreeStat> children,
  4: required map<i32, i32> line_count,    // number of times a line inside the frame was executed
}

struct TreeStats {
  1: required double sampling_interval,
  2: optional CallTreeStat call_tree,
}


struct Stats_Req {}

struct SaveSnapshot_Req {
  1: required string filepath
}


struct ProfilerRequest {
  1: required i32 id,
  2: optional Stats_Req ystats,
  3: optional SaveSnapshot_Req save_snapshot,
}

struct ProfilerResponse {
  1: required i32 id,
  2: optional Stats ystats,
  3: optional TreeStats tree_stats,
  4: optional string snapshot_filepath,
}