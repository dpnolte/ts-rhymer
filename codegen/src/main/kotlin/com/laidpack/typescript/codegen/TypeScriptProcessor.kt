package com.laidpack.typescript.codegen

import com.google.auto.service.AutoService
import javax.annotation.processing.Processor

@Suppress("unused")
@AutoService(Processor::class)
class TypeScriptProcessor : BaseTypeScriptProcessor()