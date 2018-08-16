package com.laidpack.codegen

import com.google.auto.service.AutoService
import com.laidpack.annotation.TypeScript
import com.laidpack.codegen.moshi.TargetType
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic


@AutoService(Processor::class)
class TypeScriptProcessor: KotlinAbstractProcessor(), KotlinMetadataUtils {

    companion object {
        const val OPTION_MODULE = "typescript.module"
        const val OPTION_DESTINATION = "typescript.outputDir"
        const val OPTION_INDENT = "typescript.indent"
    }

    private val annotation = TypeScript::class.java
    private var moduleName: String = "NativeTypes"
    private var indent: String = "  "
    private var customOutputDir: String? = null
    private val fileName = "types.d.ts"
    private var shouldAppendToFile = false

    override fun getSupportedAnnotationTypes() = setOf(annotation.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun getSupportedOptions() = setOf(OPTION_MODULE, OPTION_DESTINATION, OPTION_INDENT, kaptGeneratedOption)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        moduleName = processingEnv.options[OPTION_MODULE] ?: moduleName
        indent = processingEnv.options[OPTION_INDENT] ?: indent
        customOutputDir = processingEnv.options[OPTION_DESTINATION]
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val context = createContext()
        val targetedTypes = hashMapOf<String, TargetType>()
        for (element in roundEnv.getElementsAnnotatedWith(annotation)) {
            val foundTypes = TargetResolver.resolve(element, context)
            foundTypes.forEach {
                targetedTypes[it.name.simpleName] = it
            }
        }

        if (targetedTypes.isNotEmpty()) {
            val content = TypeScriptGenerator.generate(moduleName, targetedTypes, indent)
            var outputDir : String = customOutputDir ?: options[kaptGeneratedOption] ?: System.getProperty("user.dir")
            if (!outputDir.endsWith(File.separator))
                outputDir += File.separator

            val path = Paths.get(outputDir)
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Path '$outputDir' doesn't exist or is not a directory")
                return false
            }

            var file = File("$outputDir", fileName)
            file.createNewFile() // overwrite any existing file
            if (!shouldAppendToFile) {
                file.writeText(content)
                shouldAppendToFile = true
            } else {
                file.appendText(content)
            }

            messager.printMessage(Diagnostic.Kind.NOTE, "TypeScript definitions saved at $outputDir$fileName")
        }

        return true
    }

    private fun createContext(): TargetContext {
        return TargetContext(
                messager,
                elementUtils,
                typeUtils,
                typesWithinScope =  mutableSetOf(),
                typesWithTypeScriptAnnotation = mutableSetOf(),
                typesToBeAddedToScope = hashMapOf(),
                failOnError = true
        )
    }
}

internal data class TargetContext (
        val messager: Messager,
        val elementUtils: Elements,
        val typeUtils: Types,
        val typesWithinScope: MutableSet<String>,
        val typesWithTypeScriptAnnotation: MutableSet<String>,
        var typesToBeAddedToScope: MutableMap<String, TypeElement>,
        var failOnError: Boolean
) {
    var targetingTypscriptAnnotatedType = true // vs targeting a base type
}