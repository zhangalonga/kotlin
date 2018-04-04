/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.scopes.FirTypeParameterScope
import org.jetbrains.kotlin.name.Name

class FirFunctionTypeParameterScope(function: FirNamedFunction) : FirTypeParameterScope {
    override val typeParameters: Map<Name, List<FirTypeParameter>> = function.typeParameters.groupBy { it.name }
}