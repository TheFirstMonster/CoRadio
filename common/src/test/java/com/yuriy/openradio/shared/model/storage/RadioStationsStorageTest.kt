package app.coradio.shared.model.storage

import app.coradio.shared.model.media.RadioStation
import app.coradio.shared.model.translation.RadioStationJsonDeserializer
import org.hamcrest.MatcherAssert
import org.hamcrest.core.Is
import org.junit.Assert
import org.junit.Test
import java.util.TreeSet

class RadioStationsStorageTest {

    @Throws(Exception::class)
    @Test
    fun testGetAllFromString() {
        val rsStr1 = "{\"Id\":\"d28420a4-eccf-47a2-ace1-088c7e7cb7e0\",\"Name\":\"101 SMOOTH JAZZ\",\"Bitrate\":128,\"Country\":\"The United States Of America\",\"CountryCode\":\"US\",\"Genre\":\"\",\"StreamUrl\":\"http:\\/\\/www.101smoothjazz.com\\/101-smoothjazz.m3u\",\"Website\":\"http:\\/\\/101smoothjazz.com\\/\",\"IsLocal\":false,\"SortId\":243,\"ImgUrl\":\"http:\\/\\/101smoothjazz.com\\/favicon.ico\",\"Codec\":\"MP3\"}"
        val rsStr2 = "{\"Id\":\"6af0444f-22b9-11ea-aa0c-52543be04c81\",\"Name\":\"#1980s Zoom\",\"Bitrate\":128,\"Country\":\"The United Kingdom Of Great Britain And Northern Ireland\",\"CountryCode\":\"GB\",\"Genre\":\"\",\"StreamUrl\":\"https:\\/\\/samcloud.spacial.com\\/api\\/listen?sid=111415&rid=194939&f=mp3,any&br=128000,any&m=sc\",\"Website\":\"http:\\/\\/80sradio.co.uk\\/\",\"IsLocal\":false,\"SortId\":243,\"ImgUrl\":\"https:\\/\\/mytuner.global.ssl.fastly.net\\/media\\/tvos_radios\\/cjqfbpl6lyyn.png\",\"Codec\":\"MP3\"}"
        val set = TreeSet<RadioStation>()
        val deserializer = RadioStationJsonDeserializer()

        val rs1 = deserializer.deserialize(rsStr1)
        val rs2 = deserializer.deserialize(rsStr2)

        set.add(rs1)
        set.add(rs2)

        Assert.assertFalse("Ids should not match", rs1.id == rs2.id)
        Assert.assertFalse("Media Streams should not match", rs1.mediaStream == rs2.mediaStream)
        MatcherAssert.assertThat("Set should contain 2 stations", set.size, Is.`is`(2))
    }
}
