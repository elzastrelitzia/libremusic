cmake_minimum_required(VERSION 3.10)
project(quickjs-android LANGUAGES C)

set(QUICKJS_VERSION 2026-06-04)
set(QUICKJS_URL https://bellard.org/quickjs/quickjs-2026-06-04.tar.xz)

set(QUICKJS_CACHE_DIR ${CMAKE_SOURCE_DIR}/.quickjs_cache)
file(MAKE_DIRECTORY ${QUICKJS_CACHE_DIR})

set(QUICKJS_ZIP ${QUICKJS_CACHE_DIR}/quickjs-${QUICKJS_VERSION}.zip)

if (NOT EXISTS ${QUICKJS_ZIP})
    message(STATUS "Downloading QuickJS ${QUICKJS_VERSION} ...")
    file(DOWNLOAD ${QUICKJS_URL} ${QUICKJS_ZIP} SHOW_PROGRESS)
else ()
    message(STATUS "Using cached QuickJS archive: ${QUICKJS_ZIP}")
endif ()

set(SRC_EXTRACT_DIR ${CMAKE_BINARY_DIR}/quickjs-src)
set(SRC_DIR ${SRC_EXTRACT_DIR}/quickjs-${QUICKJS_VERSION})
file(MAKE_DIRECTORY ${SRC_EXTRACT_DIR})

if (NOT EXISTS ${SRC_DIR}/quickjs.c)
    message(STATUS "Extracting QuickJS source...")
    execute_process(
            COMMAND ${CMAKE_COMMAND} -E tar xzf ${QUICKJS_ZIP}
            WORKING_DIRECTORY ${SRC_EXTRACT_DIR}
    )
endif ()

file(COPY ${CMAKE_SOURCE_DIR}/repl.c DESTINATION ${SRC_DIR} NO_SOURCE_PERMISSIONS)

set(QUICKJS_SOURCES
        ${SRC_DIR}/quickjs.c
        ${SRC_DIR}/qjs.c
        ${SRC_DIR}/dtoa.c
        ${SRC_DIR}/libregexp.c
        ${SRC_DIR}/libunicode.c
        ${SRC_DIR}/cutils.c
        ${SRC_DIR}/quickjs-libc.c
        ${SRC_DIR}/repl.c
)

add_definitions(-DANDROID -DCONFIG_VERSION=\"${QUICKJS_VERSION}\")
add_compile_options(-fPIE -fPIC -O2 -Wall -Wno-missing-field-initializers)

add_executable(qjs ${QUICKJS_SOURCES})
set_target_properties(qjs PROPERTIES LINKER_LANGUAGE C)
target_link_options(qjs PRIVATE -static -pie)
target_link_libraries(qjs PRIVATE dl m)
set_target_properties(qjs PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin/${ANDROID_ABI}
)

add_custom_command(TARGET qjs POST_BUILD
        COMMAND "${CMAKE_STRIP}" "$<TARGET_FILE:qjs>"
        COMMENT "Stripping qjs binary for ${ANDROID_ABI}"
)

add_custom_command(TARGET qjs POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:qjs> ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libqjs.so
        COMMENT "Installing libqjs.so for ${ANDROID_ABI}"
)

add_library(dummy SHARED dummy.c)
add_dependencies(dummy qjs)
