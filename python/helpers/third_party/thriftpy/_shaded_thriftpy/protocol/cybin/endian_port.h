
#if defined(__APPLE__)

#include <libkern/OSByteOrder.h>

#define htobe16(x) OSSwapHostToBigInt16(x)
#define htobe32(x) OSSwapHostToBigInt32(x)
#define htobe64(x) OSSwapHostToBigInt64(x)
#define be16toh(x) OSSwapBigToHostInt16(x)
#define be32toh(x) OSSwapBigToHostInt32(x)
#define be64toh(x) OSSwapBigToHostInt64(x)

#else

#include <endian.h>
#include <byteswap.h>

#ifndef htobe16
#define htobe16(x) bswap_16(x)
#endif

#ifndef htobe32
#define htobe32(x) bswap_32(x)
#endif

#ifndef htobe64
#define htobe64(x) bswap_64(x)
#endif

#ifndef be16toh
#define be16toh(x) bswap_16(x)
#endif

#ifndef be32toh
#define be32toh(x) bswap_32(x)
#endif

#ifndef be64toh
#define be64toh(x) bswap_64(x)
#endif

#endif
