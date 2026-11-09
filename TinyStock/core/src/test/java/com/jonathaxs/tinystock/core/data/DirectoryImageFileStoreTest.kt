package com.jonathaxs.tinystock.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DirectoryImageFileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun gravaLeEApagaSoDentroDaPasta() = runBlocking {
        val directory = File(folder.root, "images")
        val images = DirectoryImageFileStore(directory)

        val name = images.write(byteArrayOf(0x01, 0x02))
        val other = images.write(byteArrayOf(0x03))

        assertTrue(name != other)
        assertArrayEquals(byteArrayOf(0x01, 0x02), images.read(name))
        assertEquals(setOf(name, other), directory.list()?.toSet())

        images.delete(name)
        assertNull(images.read(name))
        images.delete("nao-existe.jpg")

        // Nome com pasta nao sai do diretorio.
        File(folder.root, "fora.jpg").writeBytes(byteArrayOf(0x09))
        assertNull(images.read("../fora.jpg"))
        images.delete("../fora.jpg")
        assertTrue(File(folder.root, "fora.jpg").exists())
    }
}
