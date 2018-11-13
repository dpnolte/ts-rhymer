package com.laidpack.typescript.codegen

import com.laidpack.typescript.annotation.TypeScript
import com.laidpack.typescript.codegen.moshi.ITargetType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asTypeName
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
typealias SuperTypeTransformer = (superClassName: ClassName, currentModuleName: String) -> String
typealias DefinitionTypeTransformer = (className: ClassName) -> String

abstract class BaseTypeScriptProcessor : KotlinAbstractProcessor(), KotlinMetadataUtils {
    private val annotation = TypeScript::class.java
    private var moduleName: String = "NativeTypes"
    private var namespace: String? = null
    private var indent: String = "  "
    private var customOutputDir: String? = null
    private var fileName = "types.d.ts"
    private lateinit var moduleOption: ModuleOption
    private lateinit var name: String
    protected open val customTransformers: List<TypeTransformer> = listOf()
    protected open val filePreProcessors: List<FileProcessor> = listOf()
    protected open val filePostProcessors: List<FileProcessor> = listOf()
    protected open val definitionPreProcessors: List<DefinitionProcessor> = listOf()
    protected open val definitionPostProcessors: List<DefinitionProcessor> = listOf()
    protected open val definitionTypeTransformer: DefinitionTypeTransformer = { c -> c.simpleName}
    protected open val superTypeTransformer: SuperTypeTransformer = { c, _ -> c.simpleName}
    protected open val constrainToCurrentModulePackage: Boolean = false
    protected open val exportDefinitions: Boolean = false
    protected open val inAmbientDefinitionFile: Boolean = true
    protected var importWithinModule = false


    override fun getSupportedAnnotationTypes() = setOf(annotation.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    override fun getSupportedOptions() = setOf(
            OPTION_MODULE, OPTION_NAMESPACE, OPTION_OUTPUTDIR, OPTION_INDENT, OPTION_FILENAME, kaptGeneratedOption
    )

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        moduleName = processingEnv.options[OPTION_MODULE] ?: moduleName
        namespace = processingEnv.options[OPTION_NAMESPACE]
        indent = processingEnv.options[OPTION_INDENT] ?: indent
        customOutputDir = processingEnv.options[OPTION_OUTPUTDIR]
        fileName = processingEnv.options[OPTION_FILENAME] ?: fileName
        importWithinModule = processingEnv.options[OPTION_IMPORT_WITHIN_MODULE]?.toBoolean() ?: false
        when {
            processingEnv.options[OPTION_MODULE] != null -> {
                moduleOption = ModuleOption.Namespace
                name = moduleName
            }
            processingEnv.options[OPTION_NAMESPACE] != null -> {
                moduleOption = ModuleOption.Namespace
                name = namespace as String
            }
            else -> {
                ModuleOption.None
                name = ""
            }
        }
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
                    name,
                    moduleOption,
                    targetedTypes,
                    indent,
                    customTransformers,
                    constrainToCurrentModulePackage,
                    rootPackageNames,
                    packageNames,
                    filePreProcessors,
                    filePostProcessors,
                    definitionPreProcessors,
                    definitionPostProcessors,
                    definitionTypeTransformer,
                    superTypeTransformer,
                    exportDefinitions,
                    inAmbientDefinitionFile
            )
            var outputDir : String = customOutputDir ?: options[kaptGeneratedOption] ?: System.getProperty("user.dir")
            if (!outputDir.endsWith(File.separator))
                outputDir += File.separator

            val path = Paths.get(outputDir)
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Path '$outputDir' doesn't exist or is not a directory")
                return false
            }

            writeFile(outputDir, fileName, content)
        }

        return true
    }

    open fun writeFile(outputDir: String, fileName: String, content: String) {
        val file = File(outputDir, fileName)
        file.createNewFile() // overwrite any existing file
        file.writeText(content)

        messager.printMessage(Diagnostic.Kind.OTHER, "TypeScript definitions saved at $file")
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
        const val OPTION_MODULE = "typescript.module"
        const val OPTION_NAMESPACE= "typescript.namespace"
        const val OPTION_OUTPUTDIR = "typescript.outputDir"
        const val OPTION_INDENT = "typescript.indent"
        const val OPTION_FILENAME = "typescript.filename"
        const val OPTION_IMPORT_WITHIN_MODULE = "typescript.import_within_module"
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