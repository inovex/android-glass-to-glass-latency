# SPDX-License-Identifier: MIT
# SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

project(pixeltorch C)

set(CMAKE_C_STANDARD 11)

add_library(pixeltorch STATIC libpixeltorch.c)

target_compile_options(pixeltorch PUBLIC -Wall -Werror -Wextra)
target_include_directories(pixeltorch PUBLIC includes)
target_include_directories(pixeltorch PRIVATE msm8998-kernel-headers)

add_executable(flasher flasher.c)
target_compile_options(flasher PUBLIC -Wall -Werror -Wextra)
target_link_libraries(flasher PUBLIC pixeltorch)
