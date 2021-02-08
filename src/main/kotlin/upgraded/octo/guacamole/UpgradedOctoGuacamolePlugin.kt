/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package upgraded.octo.guacamole

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.lang.StringBuilder
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory


object Pom {
    val artifact = "pom-scijava"
    val version = "30.0.0"
}

class UpgradedOctoGuacamolePlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {

        settings.dependencyResolutionManagement.versionCatalogs.parsePom(Pom.artifact, Pom.version)

        // clean versions?
    }
}

fun readPom(artifact: String, version: String): String {
    val domain = "https://maven.scijava.org/content/groups/public/org/scijava/"
    val spec = "$domain$artifact/$version/$artifact-$version.pom"
    return URL(spec).readText()
}

fun MutableVersionCatalogContainer.parsePom(artifact: String, version: String) {

    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val pom = readPom(artifact, version)
    val doc = dBuilder.parse(InputSource(StringReader(pom)))

    //optional, but recommended
    //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
    doc.documentElement.normalize()

    for (i in 0 until doc.documentElement.childNodes.length) {
        val item = doc.documentElement.childNodes.item(i)

        when (item.nodeName) {
            "parent" -> parseParent(item)
            "properties" -> parseProps(item)
            "dependencyManagement" -> {
                for (j in 0 until item.childNodes.length) {
                    val deps = item.childNodes.item(j)
                    if (deps.nodeType == Node.ELEMENT_NODE) // <dependencies/>
                        parseDeps(deps)
                }
            }
        }
    }
}

fun MutableVersionCatalogContainer.parseParent(node: Node) {
    val (group, art, vers) = node.gav
    if (group == "org.scijava")
        parsePom(art, vers)
}

val Node.gav: Array<String>
    get() {
        lateinit var group: String
        lateinit var art: String
        lateinit var vers: String

        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)

            if (child.nodeType == Node.ELEMENT_NODE)
                when (child.nodeName) {
                    "groupId" -> group = child.textContent
                    "artifactId" -> art = child.textContent
                    "version" -> vers = child.textContent
                }
        }
        return arrayOf(group, art, vers)
    }

fun parseProps(node: Node) {

    for (i in 0 until node.childNodes.length) {
        val prop = node.childNodes.item(i)

        if (prop.nodeType == Node.ELEMENT_NODE && prop.nodeName.endsWith(".version")) {

            val dep = prop.nodeName.dropLast(8)
            val content = prop.textContent
            versions[dep] = when {
                content.startsWith("\${") && content.endsWith(".version}") -> { // ${imagej1.version}
                    val resolve = content.drop(2).dropLast(9)
                    versions[resolve] ?: error("cannot resolve $resolve")
                }
                else -> content
            }
        }
    }
}

enum class Lib(custom: String? = null) {
    libs, sciJava, imagej, imglib2, scifio("scif");

    val ref = custom ?: name

    val isLast: Boolean
        get() = values().last() == this

    val next: Lib
        get() = values()[ordinal + 1]
}

operator fun String.contains(lib: Lib) = contains(lib.ref, ignoreCase = true)

fun MutableVersionCatalogContainer.parseDeps(node: Node) {

    var lib = Lib.values()[1] // libs is default, let's fill first all the others that come first in the given order

    fun catalog(group: String): VersionCatalogBuilder {
        lib = when {
            lib in group -> lib // sciJava in org.scijava
            !lib.isLast -> {  // current lib is terminated
                lib = lib.next
                lib
            }
            else -> Lib.libs // default, misc
        }
        return findByName(lib.ref) ?: create(lib.ref)
    }

    for (i in 0 until node.childNodes.length) {
        val dep = node.childNodes.item(i)

        if (dep.nodeType == Node.ELEMENT_NODE) {

            val (group, artifact, vers) = dep.gav
            val version = versions[vers.drop(2).dropLast(9)]!! // ${batch-processor.version}
            val dupl = group.substringAfterLast('.')
            // org.scijava:scijava-cache
            // net.imagej:imagej
            // io.scif:scifio
            var art = artifact
            if (art.startsWith(dupl))
                art = art.drop(dupl.length).ifEmpty { "core" }
            if (art[0] == '-')
                art = art.drop(1)
            val gav = "$group:$artifact:$version"
            if (gav !in deps) { // skip duplicates, ie <classifier>tests</classifier>
                deps += gav
                catalog(group).alias(art).to(gav)
            }
        }
    }
}

val versions = mutableMapOf<String, String>()
val deps = mutableSetOf<String>()

// Anisotropic_Diffusion_2D
// Arrow_
val String.camelCase: String
    get() {
        val builder = StringBuilder()
        var capitalize = false
        for (c in this)
            if (c == '-' || c == '_')
                capitalize = true
            else {
                builder.append(when {
                    capitalize -> {
                        capitalize = false
                        c.toUpperCase()
                    }
                    else -> c
                })
            }
        return builder.toString()
    }