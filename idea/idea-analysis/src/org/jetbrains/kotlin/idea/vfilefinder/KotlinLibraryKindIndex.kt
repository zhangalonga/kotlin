/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumDataDescriptor
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment

enum class KnownLibraryKindForIndex {
    COMMON, JS
}

object KotlinLibraryKindIndex : SingleEntryFileBasedIndexExtension<KnownLibraryKindForIndex>() {
    private val KEY: ID<Int, KnownLibraryKindForIndex> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinLibraryKindIndex")
    private const val VERSION = 1
    private val VALUE_EXTERNALIZER = EnumDataDescriptor(KnownLibraryKindForIndex::class.java)

    private object MyIndexer : SingleEntryIndexer<KnownLibraryKindForIndex>(true) {
        override fun computeValue(inputData: FileContent) = detectLibraryKindFromJarContentsForIndex(inputData.file)
    }

    override fun getValueExternalizer(): DataExternalizer<KnownLibraryKindForIndex> = VALUE_EXTERNALIZER

    override fun getName() = KEY

    override fun getVersion(): Int = VERSION

    override fun getIndexer(): SingleEntryIndexer<KnownLibraryKindForIndex> = MyIndexer

    override fun getInputFilter() = FileBasedIndex.InputFilter { file -> file.extension == "jar" && file is VirtualFileWithId }

    fun getKindForFile(jarFile: VirtualFile): KnownLibraryKindForIndex? {
        var result: KnownLibraryKindForIndex? = null

        FileBasedIndex.getInstance().processValues(
            name,
            FileBasedIndex.getFileId(jarFile),
            jarFile,
            { _, value ->
                result = value
                false
            },
            EverythingGlobalScope()
        )

        return result
    }
}

private fun detectLibraryKindFromJarContentsForIndex(jarRoot: VirtualFile): KnownLibraryKindForIndex? {
    var result: KnownLibraryKindForIndex? = null
    VfsUtil.visitChildrenRecursively(jarRoot, object : VirtualFileVisitor<Unit>() {
        override fun visitFile(file: VirtualFile): Boolean =
            when (file.extension) {
                "class" -> false

                "kjsm" -> {
                    result = KnownLibraryKindForIndex.JS
                    false
                }

                MetadataPackageFragment.METADATA_FILE_EXTENSION -> {
                    result = KnownLibraryKindForIndex.COMMON
                    false
                }

                else -> true
            }
    })
    return result
}
