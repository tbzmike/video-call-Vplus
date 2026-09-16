cmake_minimum_required(VERSION 3.22.1)
project("vplus_mixer")

add_library(vplus_mixer SHARED vplus_mixer.cpp)
target_link_libraries(vplus_mixer android log dl)

add_executable(vplus_mixer_tool vplus_mixer_tool.cpp)
target_link_libraries(vplus_mixer_tool log dl)
set_target_properties(vplus_mixer_tool PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/mixer-bin/${ANDROID_ABI}")
