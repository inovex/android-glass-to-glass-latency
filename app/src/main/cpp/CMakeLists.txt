# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

cmake_minimum_required(VERSION 3.10.2)

add_subdirectory(../../../../pixeltorch pixeltorch)
add_subdirectory(../../../../external/aminilog aminilog)

project(latencytest CXX)

add_library(${PROJECT_NAME} SHARED clock.cpp pixeltorch.cpp)
target_link_libraries(${PROJECT_NAME} android pixeltorch aminilog)
target_compile_options(${PROJECT_NAME} PUBLIC -Wall -Werror -Wextra)
