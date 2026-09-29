package com.amosley.signal.data

import kotlinx.serialization.KSerializer
import java.io.File

/** Tiny atomic JSON file persistence. */
class JsonStore<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    fun load(): T = runCatching {
        if (file.exists()) SignalJson.decodeFromString(serializer, file.readText()) else default()
    }.getOrElse { default() }

    @Synchronized
    fun save(value: T) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(SignalJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
