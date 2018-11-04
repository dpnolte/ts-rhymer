package com.laidpack.typescript.codegen

import com.laidpack.annotation.TypeScript
import com.laidpack.typescript.codegen.moshi.ITargetType
import com.squareup.kotlinpoet.ClassName
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

typealias DefinitionProcessor = (targetType: ITargetType) -> String?
typealias FileProcessor = (
        targetTypes: HashMap<String, ITargetType>,
        rootPackageNames: Set<String>,
        packageNames: Set<String>
) -> String?
abstract class BaseTypeScriptProcessor(
        private val customTransformers: List<TypeTransformer> = listOf(),
        private val constrainToCurrentModulePackage: Boolean = false,
        private val filePreProcessors: List<FileProcessor> = listOf(),
        private val filePostProcessors: List<FileProcessor> = listOf(),
        private val definitionPreProcessors: List<DefinitionProcessor> = listOf(),
        private val definitionPostProcessors: List<DefinitionProcessor> = listOf()
) : KotlinAbstractProcessor(), KotlinMetadataUtils {
    private val annotation = TypeScript::class.java
    private var moduleName: String = "NativeTypes"
    private var indent: String = "  "
    private var customOutputDir: String? = null
    private var fileName = "types.d.ts"
    private var shouldAppendToFile = false

    override fun getSupportedAnnotationTypes() = setOf(annotation.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun getSupportedOptions() = setOf(OPTION_MODULE, OPTION_OUTPUTDIR, OPTION_INDENT, kaptGeneratedOption)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        moduleName = processingEnv.options[OPTION_MODULE] ?: moduleName
        indent = processingEnv.options[OPTION_INDENT] ?: indent
        customOutputDir = processingEnv.options[OPTION_OUTPUTDIR]
        fileName = processingEnv.options[OPTION_FILENAME] ?: fileName
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        val context = createContext()
        val targetedTypes = hashMapOf<String, ITargetType>()
        val rootPackageNames = mutableSetOf<String>()
        val packageNames = mutableSetOf<String>()
        for (element in roundEnv.getElementsAnnotatedWith(annotation)) {
            val typeName = element.asType().asTypeName()
            if (typeName is ClassName) {
                rootPackageNames.add(typeName.packageName)
            }
            val foundTypes = TargetResolver.resolve(element, context)
            foundTypes.forEach {
                targetedTypes[it.name.simpleName] = it
                packageNames.add(it.name.packageName)
            }
        }

        if (targetedTypes.isNotEmpty()) {
            val content = TypeScriptGenerator.generate(
                    moduleName,
                    targetedTypes,
                    indent,
                    customTransformers,
                    constrainToCurrentModulePackage,
                    rootPackageNames,
                    packageNames,
                    filePreProcessors,
                    filePostProcessors,
                    definitionPreProcessors,
                    definitionPostProcessors
            )
            var outputDir : String = customOutputDir ?: options[kaptGeneratedOption] ?: System.getProperty("user.dir")
            if (!outputDir.endsWith(File.separator))
                outputDir += File.separator

            val path = Paths.get(outputDir)
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Path '$outputDir' doesn't exist or is not a directory")
                return false
            }

            val file = File(outputDir, fileName)
            file.createNewFile() // overwrite any existing file
            if (!shouldAppendToFile) {
                file.writeText(content)
                shouldAppendToFile = true
            } else {
                file.appendText(content)
            }

            messager.printMessage(Diagnostic.Kind.OTHER, "TypeScript definitions saved at $outputDir$fileName")
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
                abortOnError = true
        )
    }
    companion object {
        private const val OPTION_MODULE = "typescript.module"
        private const val OPTION_OUTPUTDIR = "typescript.outputDir"
        private const val OPTION_INDENT = "typescript.indent"
        private const val OPTION_FILENAME = "typescript.filename"
    }
}

internal class TargetContext (
        val messager: Messager,
        val elementUtils: Elements,
        val typeUtils: Types,
        val typesWithinScope: MutableSet<String>,
        val typesWithTypeScriptAnnotation: MutableSet<String>,
        var typesToBeAddedToScope: MutableMap<String, TypeElement>,
        var abortOnError: Boolean
) {
    var targetingTypscriptAnnotatedType = true // vs targeting a base bodyType
}