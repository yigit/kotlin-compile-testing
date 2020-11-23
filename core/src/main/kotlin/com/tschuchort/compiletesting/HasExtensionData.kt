package com.tschuchort.compiletesting

import kotlin.reflect.KClass

interface HasExtensionData {
    fun <T: Any> getExtensionData(key: KClass<T>): T?

    fun <T: Any> putExtensionData(key: KClass<T>, value: T)

    fun <T: Any> getOrPutExtensionData(key: KClass<T>, build : () -> T) : T {
        return getExtensionData(key) ?: build().also {
            putExtensionData(key, it)
        }
    }

    fun copyExtensionDataInto(target: HasExtensionData)
}

internal class HasExtensionDataImpl : HasExtensionData {
    private val extensionData = mutableMapOf<KClass<*>, Any>()
    override fun <T : Any> getExtensionData(key: KClass<T>): T? {
        return extensionData[key] as? T
    }

    override fun <T : Any> putExtensionData(key: KClass<T>, value: T) {
        extensionData[key] = value
    }

    override fun copyExtensionDataInto(target: HasExtensionData) {
        extensionData.forEach {
            @Suppress("UNCHECKED_CAST")
            target.putExtensionData(it.key as KClass<Any>, it.value)
        }
    }
}