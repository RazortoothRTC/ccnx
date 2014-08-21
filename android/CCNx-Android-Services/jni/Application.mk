# Copyright (C) 2009,2010 Palo Alto Research Center, Inc.
#
# This work is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License version 2 as published by the
# Free Software Foundation.
# This work is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
# for more details. You should have received a copy of the GNU General Public
# License along with this program; if not, write to the
# Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA 02110-1301, USA.
# 

# Set APP_PLATFORM override
# You shouldn't need to change this
APP_PLATFORM := android-5

# Set APP_ABI
# You should comment this out this out if you only need the default
# default is just for armeabi
# Triples build time for JNI code and increases code size for JNI lib by 3x
APP_ABI := armeabi armeabi-v7a x86
# Please review ANDROID_NDK docs/CPU-ARCH-ABIS.html for more information.
#
# To dynamically set the APP_ABI, use 'ndk-build APP_ABI=<CPU ARCH>'

# Don't need this in a jni/Application.mk
#APP_PROJECT_PATH := 

# The libraries
APP_MODULES      := libccnx
# APP_MODULES      += lib2ccnx
APP_MODULES      += libccnd 
# APP_MODULES      += libccnsync
APP_MODULES      += libccnr 
APP_MODULES      += libcrypto
APP_MODULES      += libssl

# This is the "main" function that will route
# calls to all the linked in libraries
APP_MODULES		+=	controller

