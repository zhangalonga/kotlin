/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon

import com.intellij.util.io.isDirectory
import org.jetbrains.kotlin.daemon.DirtyRootBuilder.DirtyState.*
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class FileWatcher<T>(val roots: List<FileWatchRoot<T>>) {
    val fs = roots.first().dir.toPath().fileSystem
    val watchService = fs.newWatchService()

    val lock = ReentrantLock()
    val cond = lock.newCondition()
    val keys = mutableMapOf<WatchKey, Key<T>>()

    class Key<T>(val dir: File, val root: FileWatchRoot<T>)

    fun start(): Thread {
        roots.forEach {
            val allFiles = mutableListOf<File>()
            stepIn(it, it.dir, allFiles)
            it.dirty = DirtyRootBuilder(it, allFiles.toMutableSet(), true)
        }

        return startThread()
    }

    fun startThread(): Thread {
        return thread(start = true, isDaemon = true, name = "FileWatcher") {
            while (true) {
                val key = watchService.poll()
                if (key != null) {
                    val myKey = keys[key]!!
                    val events = key.pollEvents()
                    events?.forEach { event ->
                        println("Processing $event")
                        val kind = event.kind()
                        when (kind) {
                            OVERFLOW -> TODO("OVERFLOW")
                            else -> {
                                val file = myKey.dir.toPath().resolve(event.context() as Path).toFile()

                                if (file.isFile) {
                                    addDirty(file) {
                                        when (kind) {
                                            ENTRY_CREATE -> {
                                                handle(file, CREATED)

                                                if (file.isDirectory) {
                                                    TODO("DIR CREATED")
                                                }
                                            }
                                            ENTRY_MODIFY -> {
                                                handle(file, CHANGED)

                                                // todo: handle dir -> file
                                                // todo: handle file -> dir
                                            }
                                            ENTRY_DELETE -> {
                                                handle(file, DELETED)

                                                if (file.isDirectory) { // todo: type for deleted file?
                                                    TODO("DIR DELETE")
                                                }
                                            }
                                        }
                                    }

                                    lock.withLock {
                                        cond.signalAll()
                                    }
                                } else {
                                    check(file.isDirectory)

                                    addDirty(file) {
                                        when (kind) {
                                            ENTRY_CREATE -> TODO()
                                            ENTRY_MODIFY -> {
                                                file.listFiles().forEach {
                                                    handle(it, CHANGED)
                                                }

                                            }
                                            ENTRY_DELETE -> TODO()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stepIn(root: FileWatchRoot<T>, file: File, allFiles: MutableList<File>) {
        if (file.isDirectory) {
            val path = file.toPath()
            check(path.isDirectory())
            check(path.fileSystem == fs)

            keys[path.register(watchService, OVERFLOW, ENTRY_MODIFY, ENTRY_DELETE, ENTRY_CREATE)] = Key(file, root)

            println("Watching directory: $path")

            file.listFiles().forEach {
                stepIn(root, it, allFiles)
            }
        } else {
            allFiles.add(file)
        }
    }

    fun markDirty(file: File) {
        addDirty(file) {
            handle(file, CHANGED)
        }
    }

    fun findRoot(file: File): FileWatchRoot<T> =
        roots.first {
            file.startsWith(it.dir)
        }

    private inline fun addDirty(file: File, actions: DirtyRootBuilder<T>.() -> Unit) {
        findRoot(file).dirty.lock(actions)
    }

    fun pollDirtyRoots(): List<DirtyRoot<T>> {
        val takeDirtyRoots = takeDirtyRoots()
        if (takeDirtyRoots.isNotEmpty()) return takeDirtyRoots

        lock.withLock {
            cond.await()
        }
        return takeDirtyRoots()
    }

    fun takeDirtyRoots(): List<DirtyRoot<T>> = roots.mapNotNull {
        it.takeDirty()
    }
}

class FileWatchRoot<T>(val dir: File, val data: T) {
    @Volatile
    internal lateinit var dirty: DirtyRootBuilder<T>

    fun takeDirty(): DirtyRoot<T>? {
        val watchRoot = this
        dirty.lock {
            val changes = watchRoot.dirty.take()

            if (changes != null) {
                watchRoot.dirty = DirtyRootBuilder(watchRoot, changes.all.toMutableSet())
            }

            return changes
        }

        error("Unreachable")
    }

    override fun toString(): String {
        return "FileWatchRoot(dir=$dir, data=$data)"
    }
}


class DirState(
    val children: Map<String, DirState>,
    val files: Map<String, Long>
) {
    fun update(dir: File) {
        dir.listFiles().forEach {
            val dirState = children[it.name]
        }
    }
}

class DirtyRoot<T>(
    val root: FileWatchRoot<T>,
    val all: Set<File>,
    val isInitial: Boolean = true,
    val new: List<File> = listOf(),
    val modified: List<File> = listOf(),
    val removed: List<File> = listOf()
) {
    override fun toString() =
        if (isInitial) "DirtyRoot($root, isInitial=$isInitial, new=$new, modified=$modified, removed=$removed)"
        else "DirtyRoot($root, new=$new, modified=$modified, removed=$removed)"
}

class DirtyRootBuilder<T>(val root: FileWatchRoot<T>, val all: MutableSet<File>, val initial: Boolean = false) {
    val dirty = mutableMapOf<File, DirtyState>()

    enum class DirtyState {
        CREATED,
        CHANGED,
        DELETED
    }

    inline fun lock(actions: DirtyRootBuilder<T>.() -> Unit) {
        // todo: CAS
        synchronized(this) {
            actions()
        }
    }

    fun handle(file: File, state: DirtyState) {
        check(file in all) { "Untracked file was $state: $file" }

        val prevState = dirty[file]
        if (prevState == null) {
            dirty[file] = state
        }
    }

    private fun getDirty(state: DirtyState): List<File> =
        dirty.mapNotNull { if (it.value == state) it.key else null }

    fun take(): DirtyRoot<T>? {
        if (!initial && dirty.isEmpty()) return null

        return DirtyRoot(
            root,
            all,
            isInitial = initial,
            new = getDirty(CREATED),
            modified = getDirty(CHANGED),
            removed = getDirty(DELETED)
        )
    }

    override fun toString(): String {
        return "DirtyRootBuilder(root=$root, dirty=$dirty)"
    }
}