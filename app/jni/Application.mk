APP_ABI := arm64-v8a
APP_PLATFORM := android-24
APP_OPTIM := release
# Static libc++: NDK r25's prebuilt libc++_shared.so is only 4 KB aligned
APP_STL := c++_static
APP_CPPFLAGS := -std=c++14 -frtti -fexceptions
# 16 KB page-size devices (Android 15+): align ELF load segments to 16 KB
APP_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
