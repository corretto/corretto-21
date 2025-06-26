#
# Copyright Amazon.com Inc. or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
#

FILELIST = com/amazon/jvm/codecache/*.java com/amazon/jvm/*.java com/amazon/jvm/profile/*.java
# src/utils/HotCodeAgent/src/com/amazon/jvm/codecache/*.java src/utils/HotCodeAgent/src/com/amazon/jvm/*.java src/utils/HotCodeAgent/src/com/amazon/jvm/profile/*.java

ifneq "x$(ALT_BOOTDIR)" "x"
  BOOTDIR := $(ALT_BOOTDIR)
endif

ifeq "x$(BOOTDIR)" "x"
  JDK_HOME := $(shell dirname $(shell which java))/..
else
  JDK_HOME := $(BOOTDIR)
endif

isUnix := $(shell test -r c:/; echo $$?)

ifeq "$(isUnix)" "1"
    CPS := :
else
    CPS := ";"
endif

SRC_DIR    = src
BUILD_DIR  = build
OUTPUT_DIR = $(BUILD_DIR)/classes

ALLFILES := $(patsubst %,$(SRC_DIR)/%,$(FILELIST))
ALLFILES := $(shell /bin/ls $(ALLFILES))

JAVAC = $(JDK_HOME)/bin/javac
JAR = $(JDK_HOME)/bin/jar

all: hca.jar

hca.jar: filelist manifest.mf
	@mkdir -p $(OUTPUT_DIR)
	$(JAVAC) -deprecation -sourcepath $(SRC_DIR) -d $(OUTPUT_DIR) @filelist
	$(JAR) cvfm hca.jar manifest.mf -C $(OUTPUT_DIR) com

.PHONY: filelist
filelist: $(ALLFILES)
	@rm -f $@
	@echo $(ALLFILES) > $@

clean::
	rm -rf filelist hca.jar
	rm -rf $(BUILD_DIR)
