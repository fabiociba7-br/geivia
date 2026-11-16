cmake_minimum_required(VERSION 3.18.1)
project(geivia_proot_native)

set(PROOT_ENGINE_SOURCE ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/pr/android/proot-engine/src/main/cpp)
add_subdirectory(${PROOT_ENGINE_SOURCE} ${CMAKE_CURRENT_BINARY_DIR}/upstream-ptyjni)

add_library(ptykill SHARED ptykill.c)
target_compile_features(ptykill PRIVATE c_std_11)
target_link_libraries(ptykill log)
