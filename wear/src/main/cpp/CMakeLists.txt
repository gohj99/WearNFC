cmake_minimum_required(VERSION 3.22.1)
project(ksu_profile_helper C)

add_executable(ksu_profile_helper ksu_profile_helper.c)
target_compile_features(ksu_profile_helper PRIVATE c_std_11)
target_compile_options(ksu_profile_helper PRIVATE -Wall -Wextra -Werror)
target_link_options(ksu_profile_helper PRIVATE -static)

if(NOT HELPER_OUTPUT_DIRECTORY)
    message(FATAL_ERROR "HELPER_OUTPUT_DIRECTORY must be set by Gradle")
endif()

set_target_properties(ksu_profile_helper PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${HELPER_OUTPUT_DIRECTORY}/${ANDROID_ABI}"
)
