package com.tschuchort.compiletesting

import okio.Buffer
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream

class MessageStream {
    /**
     * Helpful information (if [verbose] = true) and the compiler
     * system output will be written to this stream
     */
    var outputStream: OutputStream = System.out

    /** Print verbose logging info */
    var verbose: Boolean = false
    /* This internal buffer and stream is used so it can be easily converted to a string
    that is put into the [Result] object, in addition to printing immediately to the user's
    stream. */
    protected val internalMessageBuffer = Buffer()

    fun createMessageCollector() = PrintingMessageCollector(
        internalMessageStream, MessageRenderer.GRADLE_STYLE, verbose
    )

    // might want to open this up
    private val internalMessageStream = PrintStream(
        TeeOutputStream(
            object : OutputStream() {
                override fun write(b: Int) = outputStream.write(b)
                override fun write(b: ByteArray) = outputStream.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = outputStream.write(b, off, len)
                override fun flush() = outputStream.flush()
                override fun close() = outputStream.close()
            },
            internalMessageBuffer.outputStream()
        )
    )

    fun log(s: String) {
        if (verbose)
            internalMessageStream.println("logging: $s")
    }

    fun warn(s: String) = internalMessageStream.println("warning: $s")
    fun error(s: String) = internalMessageStream.println("error: $s")
    fun captureProcessoutputs(proc: Process) {
        proc.inputStream.copyTo(internalMessageStream)
        proc.errorStream.copyTo(internalMessageStream)
    }

    fun createWriter() = OutputStreamWriter(internalMessageStream)

    fun collectLog() = internalMessageBuffer.readUtf8()
}