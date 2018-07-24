package idea

import org.jetbrains.kotlin.buildUtils.idea.DistVFile

class DistModelBuildContext(
        val parent: DistModelBuildContext?,
        val kind: String,
        val title: String,
        val report: Appendable? = parent?.report,
        val shade: Boolean = parent?.shade ?: false
) {
    var destination: DistVFile? = parent?.destination // todo: don't nest destination between tasks visiting
    val logPrefix: String = if (parent != null) "${parent.logPrefix}-" else ""

    init {
        report?.appendln(toString())
    }

    fun log(kind: String, title: String = "", print: Boolean = false) {
        report?.appendln("$logPrefix- $kind $title")
        if (print) {
            println("$kind $title, while visiting:")
            var p = this
            while (p.parent != null) {
                println(" - ${p.kind} ${p.title}")
                p = p.parent!!
            }
        }
    }

    fun logUnsupported(kind: String, obj: Any? = null) {
        val objInfo = if (obj != null) {
            val javaClass = obj.javaClass
            val superclass = javaClass.superclass as Class<*>
            "$obj [$javaClass extends $superclass implements ${javaClass.interfaces.map { it.canonicalName }}]"
        } else ""

        log("UNSUPPORTED $kind", objInfo, true)
    }

    override fun toString() = "$logPrefix $kind $title"

    fun child(kind: String, title: String = "", shade: Boolean = false) =
            DistModelBuildContext(this, kind, title, shade = shade)
}

