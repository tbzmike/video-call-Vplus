cmake_minimum_required(VERSION 3.22.1)
project("vplus_mixer")

add_library(vplus_mixer SHARED vplus_mixer.cpp)

target_link_libraries(vplus_mixer android log dl)
