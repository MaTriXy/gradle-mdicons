package com.tmiyamon.mdicons.repository

import com.tmiyamon.mdicons.Extension
import com.tmiyamon.mdicons.MaterialDesignIconsPlugin
import com.tmiyamon.mdicons.Result
import java.io.File
import com.tmiyamon.mdicons.ext.*
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.ObjectUtils
import org.apache.commons.lang.StringUtils
import org.gradle.api.Project
import org.gradle.util.GFileUtils
import java.io.File
import java.io.FileFilter
import java.util.LinkedHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern

class MaterialDesignIcons(val rootDir: File) {
    companion object {
        val URL = "https://github.com/google/material-design-icons.git"
        val DENSITIES = array("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        val CATEGORIES = array("action", "alert", "av", "communication", "content", "device", "editor", "file", "hardware", "image", "maps", "navigation", "notification", "social", "toggle")
        val COLORS = array("white", "black", "grey600")
        val BASE_COLOR = "white"
        val BASE_DENSITY = DENSITIES[0]
        val DENSITY_DIR_NAME_PREFIX = "drawable-"

        val ALL_FILE_FILTER = FileFilter { true }
        val BASE_COLOR_FILE_FILITER = FileFilter { BASE_COLOR_FILE_NAME_PATTERN.matcher(it.name).matches() }
        val FILE_NAME_PATTERN = Pattern.compile("""(.+)_([^_]+)_([^_]+)\.(.+)""")
        val BASE_COLOR_FILE_NAME_PATTERN = Pattern.compile(".*white.*")

        fun convertToDensityDirNameFrom(density: String): String {
            return "${DENSITY_DIR_NAME_PREFIX}${density}"
        }

        fun parseFileName(fileName: String): List<String> {
            return FILE_NAME_PATTERN.matchGroups(fileName)
        }
        fun buildFileName(name: String, color: String, size: String, ext: String): String{
            return "${name}_${color}_${size}.${ext}"
        }
    }

    fun gitClone(): Int {
        val p = ProcessBuilder("git", "clone", URL, rootDir.getAbsolutePath())
        return p.start().waitFor()
    }

    fun eachIconDir(
            categories: Array<String> = CATEGORIES,
            densities: Array<String> = DENSITIES,
            block: (String, String, File) -> Unit) {

       categories.forEach { category ->
           densities.forEach {  density ->
               val densityDirName =  convertToDensityDirNameFrom(density)
               block(category, density, File(rootDir, pathJoin(category, densityDirName)))
           }
       }
    }

    fun eachIconFile(
            categories: Array<String> = CATEGORIES,
            densities: Array<String> = DENSITIES,
            filter: FileFilter = ALL_FILE_FILTER,
            block: (String, String, File, File) -> Unit) {

        eachIconDir(categories, densities, { category, density, dir ->
            dir.listFiles(filter).forEach { file -> block(category, density, dir, file) }
        })
    }

    fun eachIcon(
            categories: Array<String> = CATEGORIES,
            densities: Array<String> = DENSITIES,
            filter: FileFilter = ALL_FILE_FILTER,
            block: (Icon) -> Unit) {

        eachIconFile(categories, densities, filter) { category, density, dir, file ->
            block(newIcon(category, density, file.name))
        }
    }

    fun eachBaseIcon(
            categories: Array<String> = CATEGORIES,
            filter: FileFilter = ALL_FILE_FILTER,
            block: (Icon) -> Unit) {

        val filterWithBaseColor = composeFileFilter(filter, BASE_COLOR_FILE_FILITER)
        val density = BASE_DENSITY

        categories.flatMap { category ->
            val densityDirName =  convertToDensityDirNameFrom(density)
            val dir = File(rootDir, pathJoin(category, densityDirName))

            dir.listFiles(filterWithBaseColor).map { file ->
               newIcon(category, density, file.name)
            }
        }.forEach { block(it) }
    }


    fun iconMatchGroupByDensity(filter: FileFilter): Map<String, List<Icon>> {
        val results: MutableMap<String, MutableList<Icon>> = linkedMapOf()

        eachIconDir(CATEGORIES, DENSITIES) { category, density, srcDir ->
            val icons = srcDir.listFiles(filter).map { newIcon(category, density, it.name) }
            results.getOrPut(density, { arrayListOf() }).addAll(icons)
        }
        return results
    }

    fun copyIconFileMatch(project: Project, filter: FileFilter): List<Icon> {
        val results = arrayListOf<Icon>()

        for ( (density, icons) in iconMatchGroupByDensity(filter)) {

            MaterialDesignIconsPlugin.logger.debug("$density: $icons")

            icons.forEach {
                val srcFile = it.toFile()
                val dstFile = project.file(it.getDestinationRelativePath())

                dstFile.getParentFile().mkdirs()

                try {
                    GFileUtils.copyFile(srcFile, dstFile)
                    results.add(it)
                } catch (e: Exception) {
                    // TODO error handling
                }
            }
        }

        return results
    }

    fun copyIconFileMatchGroup(project:Project, filter: FileFilter): List<Icon> {
        val results = arrayListOf<Icon>()

        return results
    }

    fun newIcon(category: String, density: String, fileName: String): Icon {
        val (name, color, size, ext) = parseFileName(fileName)
        return Icon(
            category = category,
            density = density,
            name = name,
            color = color,
            size = size,
            ext = ext
        )
    }

    inner class Icon(
            val category: String,
            val density: String,
            val name: String,
            val color: String,
            val size: String,
            val ext: String) {

        fun getFileName(): String {
            return buildFileName(name, color, size, ext)
        }

        fun getSourceRelativePath(): String {
            return pathJoin(category, convertToDensityDirNameFrom(density), getFileName())
        }

        fun getDestinationRelativePath(): String {
            return pathJoin(Extension.PROJECT_RESOURCE_RELATIVE_PATH, convertToDensityDirNameFrom(density), getFileName())
        }

        fun toFile(): File {
            return File(rootDir, getSourceRelativePath())
        }

        fun newIconVariantsForDensities(densities: Array<String> = DENSITIES): List<Icon> {
            return densities.map { density ->
                Icon(this.category, density, this.name, this.color, this.size, this.ext)
            }
        }

        fun isBaseIcon(): Boolean {
            return density == BASE_DENSITY && color == BASE_COLOR
        }

        fun assertBaseIcon() {
            if(!isBaseIcon()) {
                throw Exception("only allowed in base icon")
            }
        }

        fun newWithColorAndGenerateIfNeeded(newColor: String): Icon {
            assertBaseIcon()
            val cloned = Icon(this.category, this.density, this.name, newColor, this.size, this.ext)
            val file = cloned.toFile()
            if (file.exists()) {
                return cloned
            }

//            val newColorHex = MaterialColor.instance.getHexFrom(newColor)
//
//            new ImageMagick()
//                    .option("-fuzz", "75%")
//                    .option('-fill', newColorHex)
//                    .option('-opaque', this.color)
//                    .option('-type', 'TrueColorMatte')
//                    .args(this.toFile().absolutePath, "PNG32:${cloned.toFile().absolutePath}")
//                    .exec()

            return cloned
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true;
            }
            if (!(other is Icon)) {
                return false;
            }

            val o = other: Icon
            return o.category == this.category &&
                    o.color == this.color &&
                    o.density == this.density &&
                    o.ext == this.ext &&
                    o.name == this.name &&
                    o.size == this.size
        }

        override fun toString(): String {
            return "MaterialDesignIcons\$Icon(category=$category,density=$density,name=$name,color=$color,size=$size,ext=$ext)"
        }
    }
}
