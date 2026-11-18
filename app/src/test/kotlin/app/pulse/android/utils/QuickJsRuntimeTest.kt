package app.pulse.android.utils

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import app.pulse.android.ensureQuickJsExecutable

class QuickJsRuntimeTest {

    @Test(expected = IllegalStateException::class)
    fun `missing quickjs runtime throws clear error`() {
        ensureQuickJsExecutable(File("/no/such/libqjs.so"))
    }

    @Test
    fun `executable quickjs runtime returns the file`() {
        val tempFile = File.createTempFile("libqjs", ".so")
        tempFile.deleteOnExit()
        tempFile.setExecutable(true)
        val result = ensureQuickJsExecutable(tempFile)
        assertEquals(tempFile.absolutePath, result.absolutePath)
    }

    @Test
    fun `non-executable quickjs runtime becomes executable`() {
        val tempFile = File.createTempFile("libqjs", ".so")
        tempFile.deleteOnExit()
        tempFile.setExecutable(false)
        val result = ensureQuickJsExecutable(tempFile)
        assertEquals(tempFile.absolutePath, result.absolutePath)
        assertTrue(result.canExecute())
    }
}