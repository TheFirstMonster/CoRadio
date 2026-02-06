package app.coradio.shared.utils

import org.junit.Assert
import org.junit.Test

class AppUtilsTest {

    @Test
    fun testSerializeMap() {
        val keyA = "KEY_FAV"
        val keyB = "KEY_LOC"
        val data = hashMapOf(
            keyA to "mStorageManagerLayer.getAllFavoritesAsString()",
            keyB to "mStorageManagerLayer.getAllDeviceLocalsAsString()"
        )

        val serialized = AppUtils.serializeMap(data)

        val deserialized = AppUtils.deserializeMap(serialized)

        Assert.assertEquals(data[keyA], deserialized[keyA])
        Assert.assertEquals(data[keyB], deserialized[keyB])

        val deserializedEmpty = AppUtils.deserializeMap(null)
        Assert.assertTrue(deserializedEmpty.isEmpty())
    }
}