/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.spec

import java.io.File

class ParsingSpecTestValidator(testDataFile: File) : SpecTestValidator(testDataFile, TestArea.DIAGNOSTIC) {
    constructor(testDataFile: String) : this(File(testDataFile))
}