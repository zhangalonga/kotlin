/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parsing

import org.jetbrains.kotlin.spec.ParsingSpecTestValidator
import org.jetbrains.kotlin.spec.SpecTestValidationException
import org.jetbrains.kotlin.spec.SpecTestValidator
import org.junit.Assert

abstract class AbstractParsingTestSpec : AbstractParsingTest() {
    private lateinit var testValidator: ParsingSpecTestValidator

    override fun doParsingTest(filePath: String) {
        testValidator = ParsingSpecTestValidator(filePath)

        try {
            testValidator.validateByTestInfo()
        } catch (e: SpecTestValidationException) {
            Assert.fail(e.reason.description)
        }

        testValidator.printTestInfo()

        super.doParsingTest(filePath, SpecTestValidator::testMetaInfoFilter)

        try {
            testValidator.validateTestType(myFile)
        } catch (e: SpecTestValidationException) {
            Assert.fail(e.reason.description)
        }
    }
}
