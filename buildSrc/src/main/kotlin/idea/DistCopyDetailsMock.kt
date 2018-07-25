package idea

import groovy.lang.Closure
import org.gradle.api.Transformer
import org.gradle.api.file.ContentFilterable
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import java.io.File
import java.io.FilterReader
import java.io.InputStream
import java.io.OutputStream

class DistCopyDetailsMock(srcName: String) : FileCopyDetails {
    private var relativePath = RelativePath(true, srcName)

    override fun setDuplicatesStrategy(strategy: DuplicatesStrategy?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSourcePath(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getName(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSize(): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRelativePath(): RelativePath = relativePath

    override fun getRelativeSourcePath(): RelativePath {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun expand(properties: MutableMap<String, *>?): ContentFilterable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMode(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSourceName(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun filter(properties: MutableMap<String, *>?, filterType: Class<out FilterReader>?): ContentFilterable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun filter(filterType: Class<out FilterReader>?): ContentFilterable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun filter(closure: Closure<*>?): ContentFilterable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun filter(transformer: Transformer<String, String>?): ContentFilterable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getFile(): File {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setMode(mode: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun copyTo(output: OutputStream?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun copyTo(target: File?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun open(): InputStream {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setRelativePath(path: RelativePath?) {
        if (path != null) {
            relativePath = path
        }
    }

    override fun getPath(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isDirectory(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDuplicatesStrategy(): DuplicatesStrategy {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setName(name: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLastModified(): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setPath(path: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun exclude() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}