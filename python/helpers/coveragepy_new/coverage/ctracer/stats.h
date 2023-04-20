/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#ifndef _COVERAGE_STATS_H
#define _COVERAGE_STATS_H

#include "util.h"

#if COLLECT_STATS
#define STATS(x)        x
#else
#define STATS(x)
#endif

typedef struct Stats {
    unsigned int calls;     /* Need at least one member, but the rest only if needed. */
#if COLLECT_STATS
    unsigned int lines;
    unsigned int returns;
    unsigned int others;
    unsigned int files;
    unsigned int stack_reallocs;
    unsigned int errors;
    unsigned int pycalls;
    unsigned int start_context_calls;
#endif
} Stats;

#endif /* _COVERAGE_STATS_H */
